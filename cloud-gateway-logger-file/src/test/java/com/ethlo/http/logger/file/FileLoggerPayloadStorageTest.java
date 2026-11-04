package com.ethlo.http.logger.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;

import com.ethlo.http.logger.CaptureConfiguration;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.match.LogOptions.ContentProcessing;
import com.ethlo.http.model.AccessLogResult;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;

class FileLoggerPayloadStorageTest
{
    private static final String REQUEST_ID = "abc-123";
    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    private static final String REQUEST = """
            POST /api HTTP/1.1\r
            Host: gateway\r
            Content-Type: application/json\r
            Content-Length: 13\r
            \r
            {"hello":"x"}""";

    private static final String RESPONSE = """
            HTTP/1.1 200 OK\r
            Content-Type: text/plain\r
            Content-Length: 2\r
            \r
            ok""";

    private static final String TRUNCATED_CHUNKED_RESPONSE = """
            HTTP/1.1 200 OK\r
            Transfer-Encoding: chunked\r
            \r
            19\r
            event: message\ndata: hi\n\r
            """;

    @TempDir
    private Path captureDirectory;

    @TempDir
    private Path storageDirectory;

    private DataBufferRepository dataBufferRepository;

    @BeforeEach
    void setUp() throws IOException
    {
        final CaptureConfiguration captureConfiguration = new CaptureConfiguration();
        captureConfiguration.setLogDirectory(captureDirectory);
        dataBufferRepository = new DataBufferRepository(captureConfiguration);
    }

    @Test
    void storesBodiesAndRawAsPlainFiles() throws IOException
    {
        capture(REQUEST, RESPONSE);
        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        final Path day = storageDirectory.resolve("2026-11-04");
        assertThat(day.resolve(REQUEST_ID + ".request.body")).hasContent("{\"hello\":\"x\"}");
        assertThat(day.resolve(REQUEST_ID + ".response.body")).hasContent("ok");
        assertThat(Files.readString(day.resolve(REQUEST_ID + ".request.raw"))).isEqualTo(REQUEST);
        assertThat(Files.readString(day.resolve(REQUEST_ID + ".response.raw"))).isEqualTo(RESPONSE);
    }

    @Test
    void filesAreOnlyReadableByTheOwner() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        capture(REQUEST, RESPONSE);
        accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.STORE);

        final Path day = storageDirectory.resolve("2026-11-04");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(day))).isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(day.resolve(REQUEST_ID + ".request.raw")))).isEqualTo("rw-------");
        assertThat(day).isDirectoryNotContaining("glob:**.tmp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rwxrwxrwx", "rwxrwx---", "rwx---rwx"})
    void refusesDayDirectoryWritableByOthers(final String permissions) throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        capture(REQUEST, RESPONSE);
        final Path day = Files.createDirectory(storageDirectory.resolve("2026-11-04"));
        Files.setPosixFilePermissions(day, PosixFilePermissions.fromString(permissions));

        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.NONE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains("writable by other users"));
        assertThat(day).isEmptyDirectory();
    }

    @ParameterizedTest
    @ValueSource(ints = {0777, 0770, 0707})
    void refusesStorageDirectoryWritableByOthersWithoutStickyBit(final int mode) throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        capture(REQUEST, RESPONSE);
        Files.setAttribute(storageDirectory, "unix:mode", mode);

        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.NONE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains("writable by other users without the sticky bit"));
        assertThat(storageDirectory).isEmptyDirectory();
    }

    @Test
    void acceptsStorageDirectoryWritableByOthersWithStickyBit() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        capture(REQUEST, RESPONSE);
        Files.setAttribute(storageDirectory, "unix:mode", 01777);

        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        assertThat(storageDirectory.resolve("2026-11-04").resolve(REQUEST_ID + ".request.body")).hasContent("{\"hello\":\"x\"}");
    }

    @Test
    void refusesStorageDirectoryOwnedBySomeoneElse() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        final int me = (Integer) Files.getAttribute(storageDirectory, "unix:uid");
        assumeTrue(me != 0 && me != 65534 && me != (Integer) Files.getAttribute(Path.of("/"), "unix:uid"));
        capture(REQUEST, RESPONSE);

        final LogOptions options = new LogOptions(null, ContentProcessing.STORE, ContentProcessing.NONE);
        final PredicateConfig predicateConfig = new PredicateConfig("test-matcher", AsyncPredicate.from(exchange -> true), options, options);
        final UserPrincipal someoneElse = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName("65534");
        final FileLogger logger = new FileLogger(new PebbleAccessLogTemplateRenderer("{{status}}", false), storageDirectory, someoneElse, Set.of(65534));
        final AccessLogResult result = logger.accessLog(dataProvider(predicateConfig)).join();

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains(storageDirectory.toRealPath() + " is owned by uid " + me));
        assertThat(storageDirectory).isEmptyDirectory();
    }

    @Test
    void refusesStorageDirectoryBelowADirectoryWritableByOthers() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        capture(REQUEST, RESPONSE);
        Files.setAttribute(storageDirectory, "unix:mode", 0777);
        final Path payloads = storageDirectory.resolve("payloads");

        final AccessLogResult result = accessLog(payloads, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains(storageDirectory + " is writable by other users without the sticky bit"));
        assertThat(payloads).doesNotExist();
    }

    @Test
    void refusesSymlinkInStoragePathPlacedInDirectoryWritableByOthers(@TempDir final Path elsewhere) throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        capture(REQUEST, RESPONSE);
        final Path link = Files.createSymbolicLink(storageDirectory.resolve("payloads"), elsewhere);
        Files.setAttribute(storageDirectory, "unix:mode", 0777);

        final AccessLogResult result = accessLog(link, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains(storageDirectory + " is writable by other users without the sticky bit"));
        assertThat(elsewhere).isEmptyDirectory();
    }

    @Test
    void followsSymlinkInSafeStoragePath(@TempDir final Path elsewhere) throws IOException
    {
        capture(REQUEST, RESPONSE);
        final Path link = Files.createSymbolicLink(storageDirectory.resolve("payloads"), elsewhere);

        final AccessLogResult result = accessLog(link, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        assertThat(elsewhere.resolve("2026-11-04").resolve(REQUEST_ID + ".request.body")).hasContent("{\"hello\":\"x\"}");
    }

    @Test
    void onlyChecksPermissionsWhenTheGatewayUserIsUnknown() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        final int me = (Integer) Files.getAttribute(storageDirectory, "unix:uid");
        assumeTrue(me != 0 && me != 65534 && me != (Integer) Files.getAttribute(Path.of("/"), "unix:uid"));
        final UserPrincipal someoneElse = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName("65534");

        assertThat(PayloadDirectory.prepareRoot(storageDirectory, null, directory -> someoneElse)).isEqualTo(storageDirectory.toRealPath());
        assertThatThrownBy(() -> PayloadDirectory.prepareRoot(storageDirectory, Set.of(65534), directory -> someoneElse)).hasMessageContaining("is owned by uid " + me);
    }

    @Test
    void processUidIsTheUserRunningTheTests() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        assertThat(PayloadDirectory.processUid()).contains((Integer) Files.getAttribute(storageDirectory, "unix:uid"));
    }

    @Test
    void replacesAnExistingPayloadFile() throws IOException
    {
        capture(REQUEST, RESPONSE);
        final Path day = Files.createDirectory(storageDirectory.resolve("2026-11-04"));
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        {
            Files.setPosixFilePermissions(day, PosixFilePermissions.fromString("rwx------"));
        }
        Files.writeString(day.resolve(REQUEST_ID + ".response.body"), "old");

        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        assertThat(day.resolve(REQUEST_ID + ".response.body")).hasContent("ok");
    }

    @Test
    void refusesDayDirectoryOwnedBySomeoneElse() throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        capture(REQUEST, RESPONSE);
        final UserPrincipal me = Files.getOwner(storageDirectory);
        final UserPrincipal someoneElse = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName("65534");
        assumeTrue(!someoneElse.equals(me));

        final LogOptions options = new LogOptions(null, ContentProcessing.STORE, ContentProcessing.NONE);
        final PredicateConfig predicateConfig = new PredicateConfig("test-matcher", AsyncPredicate.from(exchange -> true), options, options);
        final FileLogger logger = new FileLogger(new PebbleAccessLogTemplateRenderer("{{status}}", false), storageDirectory, someoneElse);
        final AccessLogResult result = logger.accessLog(dataProvider(predicateConfig)).join();

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains("is owned by"));
        assertThat(storageDirectory.resolve("2026-11-04")).isEmptyDirectory();
    }

    @Test
    void refusesSymlinkedDayDirectory(@TempDir final Path elsewhere) throws IOException
    {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        capture(REQUEST, RESPONSE);
        Files.createSymbolicLink(storageDirectory.resolve("2026-11-04"), elsewhere);

        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.NONE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).contains("Refusing to store payloads").contains("symbolic link"));
        assertThat(elsewhere).isEmptyDirectory();
    }

    @Test
    void writeFailureIsReported() throws IOException
    {
        capture(REQUEST, RESPONSE);
        // A regular file where the day directory should be makes every write fail
        Files.writeString(storageDirectory.resolve("2026-11-04"), "in the way");
        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.NONE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).hasSize(2).allSatisfy(e -> assertThat(e.getMessage()).startsWith("Unable to write"));
    }

    @Test
    void onlyStoresWhatTheMatcherAsksFor()
    {
        capture(REQUEST, RESPONSE);
        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.SIZE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        assertThat(storageDirectory.resolve("2026-11-04")).isDirectoryContaining(p -> p.getFileName().toString().endsWith(".body"))
                .isDirectoryNotContaining(p -> p.getFileName().toString().endsWith(".raw"));
    }

    @Test
    void skipsEmptyBodies()
    {
        capture("GET /api HTTP/1.1\r\nHost: x\r\n\r\n", RESPONSE);
        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.NONE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        final Path day = storageDirectory.resolve("2026-11-04");
        assertThat(day.resolve(REQUEST_ID + ".request.body")).doesNotExist();
        assertThat(day.resolve(REQUEST_ID + ".response.body")).hasContent("ok");
    }

    @Test
    void writesNothingWithoutStorageDirectory()
    {
        capture(REQUEST, RESPONSE);
        final AccessLogResult result = accessLog(null, ContentProcessing.STORE, ContentProcessing.STORE);

        assertThat(result.isOk()).isTrue();
        assertThat(storageDirectory).isEmptyDirectory();
    }

    @Test
    void undecodableBodyIsReportedAndRawIsStillStored()
    {
        capture(REQUEST, TRUNCATED_CHUNKED_RESPONSE);
        final AccessLogResult result = accessLog(storageDirectory, ContentProcessing.STORE, ContentProcessing.STORE);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getProcessingErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("chunk-size"));
        assertThat(storageDirectory.resolve("2026-11-04").resolve(REQUEST_ID + ".response.raw")).exists();
    }

    private void capture(final String request, final String response)
    {
        dataBufferRepository.write(ServerDirection.REQUEST, REQUEST_ID, ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8))).join();
        dataBufferRepository.write(ServerDirection.RESPONSE, REQUEST_ID, ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8))).join();
    }

    private AccessLogResult accessLog(final Path storage, final ContentProcessing raw, final ContentProcessing body)
    {
        final LogOptions options = new LogOptions(null, raw, body);
        final PredicateConfig predicateConfig = new PredicateConfig("test-matcher", AsyncPredicate.from(exchange -> true), options, options);
        final FileLogger logger = new FileLogger(new PebbleAccessLogTemplateRenderer("{{gateway_request_id}} {{status}}", false), storage);
        return logger.accessLog(dataProvider(predicateConfig)).join();
    }

    private WebExchangeDataProvider dataProvider(final PredicateConfig predicateConfig)
    {
        return new WebExchangeDataProvider(dataBufferRepository, predicateConfig)
                .route(Route.async().id("test-route").uri(URI.create("http://upstream")).predicate(exchange -> true).build())
                .requestId(REQUEST_ID)
                .method(HttpMethod.POST)
                .path(RequestPath.parse(URI.create("/api"), null))
                .uri(URI.create("http://gateway/api"))
                .statusCode(HttpStatus.OK)
                .requestHeaders(new HttpHeaders())
                .responseHeaders(new HttpHeaders())
                .timestamp(TIMESTAMP)
                .duration(Duration.ofMillis(5))
                .remoteAddress(null);
    }
}
