package com.ethlo.http.logger.file;

import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.BodyDecodeException;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.RawProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.util.HttpBodyUtil;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");
    private static final Logger logger = LoggerFactory.getLogger(FileLogger.class);
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;
    private final Path storageDirectory;
    private final UserPrincipal owner;

    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer)
    {
        this(accessLogTemplateRenderer, null);
    }

    /**
     * @param storageDirectory Where to write the request and response content that the matcher asks to <code>STORE</code>,
     *                         one file per request, direction and kind. <code>null</code> to only write the access log line.
     */
    public FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer, final Path storageDirectory)
    {
        this(accessLogTemplateRenderer, storageDirectory, storageDirectory != null && POSIX ? currentUser() : null);
    }

    /**
     * @param owner The user that day directories must belong to before payloads are written into them
     */
    FileLogger(final AccessLogTemplateRenderer accessLogTemplateRenderer, final Path storageDirectory, final UserPrincipal owner)
    {
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
        this.storageDirectory = storageDirectory;
        this.owner = owner;
    }

    /**
     * The owner of a file this process creates, which is reliable even when the user has no name, as is common in containers.
     */
    static UserPrincipal currentUser()
    {
        try
        {
            final Path probe = Files.createTempFile("cloud-gateway-owner", ".tmp");
            try
            {
                return Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
            }
            finally
            {
                Files.delete(probe);
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Unable to determine the user running the gateway", e);
        }
    }

    @Override
    public CompletableFuture<AccessLogResult> accessLog(final WebExchangeDataProvider dataProvider)
    {
        final PredicateConfig predicateConfig = dataProvider.getPredicateConfig().orElseThrow();
        final Map<String, Object> metaMap = dataProvider.asMetaMap();
        accessLogLogger.info(accessLogTemplateRenderer.render(metaMap));

        if (storageDirectory == null)
        {
            return CompletableFuture.completedFuture(AccessLogResult.ok(predicateConfig));
        }

        final List<IOException> errors = new ArrayList<>();
        store(dataProvider, predicateConfig.request(), dataProvider.getRawRequest(), REQUEST, errors);
        store(dataProvider, predicateConfig.response(), dataProvider.getRawResponse(), RESPONSE, errors);
        return CompletableFuture.completedFuture(errors.isEmpty() ? AccessLogResult.ok(predicateConfig) : AccessLogResult.error(predicateConfig, errors));
    }

    /**
     * Streams from the capture buffer file to the target files, so payloads of any size are never held in memory.
     */
    private void store(final WebExchangeDataProvider dataProvider, final LogOptions logOptions, final Optional<RawProvider> rawProvider, final ServerDirection direction, final List<IOException> errors)
    {
        if (rawProvider.isEmpty())
        {
            return;
        }

        if (logOptions.raw() == STORE)
        {
            writeFile(dataProvider, direction, "raw", rawProvider.get(), (in, out) -> in.transferTo(out)).ifPresent(errors::add);
        }

        if (logOptions.body() == STORE)
        {
            writeFile(dataProvider, direction, "body", rawProvider.get(), (in, out) -> HttpBodyUtil.writeBody(in, direction, out)).ifPresent(errors::add);
        }
    }

    private Optional<IOException> writeFile(final WebExchangeDataProvider dataProvider, final ServerDirection direction, final String kind, final RawProvider rawProvider, final ContentWriter writer)
    {
        final Path target = getFile(dataProvider, direction, kind);
        try
        {
            final Optional<InputStream> source = rawProvider.openStream();
            if (source.isEmpty())
            {
                return Optional.empty();
            }

            try (InputStream in = source.get())
            {
                Files.createDirectories(target.getParent(), ownerOnly("rwx------"));
                try (PayloadDirectory directory = PayloadDirectory.open(target.getParent(), owner))
                {
                    write(directory, target.getFileName().toString(), in, writer);
                }
            }
            logger.debug("Stored {} {} for request {} in {}", direction.name().toLowerCase(), kind, dataProvider.getRequestId(), target);
            return Optional.empty();
        }
        catch (BodyDecodeException e)
        {
            return Optional.of(e);
        }
        catch (IOException | RuntimeException e)
        {
            return Optional.of(new IOException("Unable to write " + target + ": " + e.getMessage(), e));
        }
    }

    /**
     * Written to a new temp file through the channel that created it and then moved into place, so a failed write
     * never leaves a truncated file under the expected name.
     */
    private static void write(final PayloadDirectory directory, final String name, final InputStream in, final ContentWriter writer) throws IOException
    {
        final String temp = name + "." + UUID.randomUUID() + ".tmp";
        final long written;
        try
        {
            try (OutputStream out = Channels.newOutputStream(directory.create(temp, ownerOnly("rw-------"))))
            {
                written = writer.write(in, out);
            }

            // Most requests have no body, so skip the empty files rather than clutter the directory
            if (written == 0)
            {
                directory.delete(temp);
                return;
            }
            directory.rename(temp, name);
        }
        catch (IOException | RuntimeException e)
        {
            try
            {
                directory.delete(temp);
            }
            catch (IOException | RuntimeException suppressed)
            {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    @FunctionalInterface
    private interface ContentWriter
    {
        long write(InputStream in, OutputStream out) throws IOException;
    }

    /**
     * Payloads can hold credentials, so they are only readable by the user running the gateway.
     */
    private static FileAttribute<?>[] ownerOnly(final String permissions)
    {
        return POSIX ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions))} : new FileAttribute<?>[0];
    }

    /**
     * Files are grouped in a directory per day, so old ones are easy to remove, and named after the request id that
     * is also available to the access log pattern as <code>gateway_request_id</code>.
     */
    Path getFile(final WebExchangeDataProvider dataProvider, final ServerDirection direction, final String kind)
    {
        final String day = Objects.requireNonNull(dataProvider.getTimestamp(), "timestamp").format(DateTimeFormatter.ISO_LOCAL_DATE);
        return storageDirectory.resolve(day).resolve(dataProvider.getRequestId() + "." + direction.name().toLowerCase() + "." + kind);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "', storageDirectory=" + storageDirectory;
    }
}
