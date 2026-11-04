package com.ethlo.http.logger.file;

import static com.ethlo.http.match.LogOptions.ContentProcessing.STORE;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.BodyProvider;
import com.ethlo.http.model.RawProvider;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.util.AsyncUtil;
import com.ethlo.http.util.HttpBodyUtil;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");
    private static final Logger logger = LoggerFactory.getLogger(FileLogger.class);

    private final AccessLogTemplateRenderer accessLogTemplateRenderer;
    private final Path storageDirectory;

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
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
        this.storageDirectory = storageDirectory;
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

        return AsyncUtil.join(List.of(
                store(dataProvider, predicateConfig.request(), dataProvider.getRawRequest(), REQUEST),
                store(dataProvider, predicateConfig.response(), dataProvider.getRawResponse(), RESPONSE)
        )).thenApply(results ->
        {
            final List<IOException> errors = results.stream().flatMap(List::stream).toList();
            return errors.isEmpty() ? AccessLogResult.ok(predicateConfig) : AccessLogResult.error(predicateConfig, errors);
        });
    }

    private CompletableFuture<List<IOException>> store(final WebExchangeDataProvider dataProvider, final LogOptions logOptions, final Optional<RawProvider> rawProvider, final ServerDirection direction)
    {
        if (logOptions.raw() != STORE && logOptions.body() != STORE)
        {
            return CompletableFuture.completedFuture(List.of());
        }

        return rawProvider.flatMap(RawProvider::getBuffer)
                .map(future -> future.thenApply(buffer -> write(dataProvider, logOptions, buffer.array(), direction)))
                .orElse(CompletableFuture.completedFuture(List.of()));
    }

    private List<IOException> write(final WebExchangeDataProvider dataProvider, final LogOptions logOptions, final byte[] raw, final ServerDirection direction)
    {
        final List<IOException> errors = new ArrayList<>();
        if (logOptions.raw() == STORE)
        {
            writeFile(dataProvider, direction, "raw", new ByteArrayInputStream(raw)).ifPresent(errors::add);
        }

        if (logOptions.body() == STORE)
        {
            try
            {
                final BodyProvider body = HttpBodyUtil.extractBody(new ByteArrayInputStream(raw), direction);
                // Most requests have no body, so skip the empty files rather than clutter the directory
                if (body.bodyLength() > 0)
                {
                    writeFile(dataProvider, direction, "body", body.data()).ifPresent(errors::add);
                }
            }
            catch (IOException e)
            {
                errors.add(e);
            }
        }
        return errors;
    }

    private Optional<IOException> writeFile(final WebExchangeDataProvider dataProvider, final ServerDirection direction, final String kind, final InputStream data)
    {
        final Path target = getFile(dataProvider, direction, kind);
        try (data)
        {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Stored {} {} for request {} in {}", direction.name().toLowerCase(), kind, dataProvider.getRequestId(), target);
            return Optional.empty();
        }
        catch (IOException e)
        {
            return Optional.of(new IOException("Unable to write " + target + ": " + e.getMessage(), e));
        }
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
