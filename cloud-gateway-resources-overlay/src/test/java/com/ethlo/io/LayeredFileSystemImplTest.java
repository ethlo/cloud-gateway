package com.ethlo.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.ethlo.http.io.LayeredFileSystem;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class LayeredFileSystemImplTest
{
    private Path layer1;
    private Path layer2;
    private LayeredFileSystem fileSystem;

    @BeforeEach
    void setUp() throws IOException
    {
        // Create two temporary directories (layers)
        layer1 = Files.createTempDirectory("layer1_");
        layer2 = Files.createTempDirectory("layer2_");

        // Create the LayeredFileSystem instance using the temp directories
        fileSystem = new LayeredFileSystem(List.of(layer1, layer2), Duration.ofMinutes(10));

        // Create files in different layers
        Files.writeString(layer1.resolve("file1.txt"), "layer1_file1.txt");
        Files.writeString(layer2.resolve("file2.txt"), "layer2_file2.txt");
        Files.writeString(layer2.resolve("file1.txt"), "layer2_file1.txt");
        Files.writeString(layer2.resolve("file3.txt"), "layer2_file3.txt");
        Files.createDirectory(layer2.resolve("nested"));
        Files.writeString(layer2.resolve("nested/file4.txt"), "layer2_nested_file4.txt");
    }

    @AfterEach
    void tearDown() throws IOException
    {
        // Clean up temporary directories
        PathUtils.delete(layer1);
        PathUtils.delete(layer2);

        // Shutdown the file watcher service
        fileSystem.close();
    }

    @Test
    void testListRootFiles() throws IOException
    {
        // List files in the root directory
        try (Stream<Path> files = fileSystem.list(Paths.get("")))
        {
            assertThat(files).containsExactly(
                    Path.of("file1.txt"),
                    Path.of("file2.txt"),
                    Path.of("file3.txt"),
                    Path.of("nested")
            );
        }
    }

    @Test
    void testFindFileInLayer1()
    {
        // Find file1.txt in layer1
        Optional<Resource> resource = fileSystem.find(Paths.get("file1.txt"));
        assertResource(layer1, "file1.txt", resource, "layer1_file1.txt");
    }

    private void assertResource(Path layer, String file, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Resource> resource, final String expectedContents)
    {
        assertThat(resource).isPresent();

        final Resource res = resource.get();

        assertThat(res).extracting(r -> {
                    try
                    {
                        return r.getContentAsString(StandardCharsets.UTF_8);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                })
                .isEqualTo(expectedContents);

        assertThat(res)
                .extracting(r ->
                {
                    try
                    {
                        return r.getFile().toPath().toString();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                })
                .isEqualTo(layer.resolve(file).toString());
    }

    @Test
    void testFindFileInLayer2_HappyPath()
    {
        // Find file2.txt in layer2
        Optional<Resource> resource = fileSystem.find(Paths.get("file2.txt"));
        assertResource(layer2, "file2.txt", resource, "layer2_file2.txt");
    }

    @Test
    void testFindFileNotExist()
    {
        // Try to find a file that doesn't exist
        Optional<Resource> resource = fileSystem.find(Paths.get("nonexistent.txt"));
        assertThat(resource).isNotPresent();
    }

    @Test
    void testListFilesUsesFallback() throws IOException
    {
        // Simulate IOException by deleting the layer directory before listing
        FileUtils.deleteDirectory(layer1.toFile());
        assertThat(Files.exists(layer1)).isFalse();

        assertThat(fileSystem.list(Paths.get(""))).containsExactly(
                Path.of("file1.txt"),
                Path.of("file2.txt"),
                Path.of("file3.txt"),
                Path.of("nested")
        );
    }

    @Test
    void testFileWatcherCacheInvalidation() throws IOException, InterruptedException
    {
        // Find file1.txt to add it to the cache
        final Path relativePath = Paths.get("file3.txt");
        Optional<Resource> resource = fileSystem.find(relativePath);
        assertResource(layer2, "file3.txt", resource, "layer2_file3.txt");

        // Delete file1.txt to trigger cache invalidation
        Files.delete(layer2.resolve("file3.txt"));

        // Wait for the file watcher to trigger cache invalidation
        Thread.sleep(200);  // Adjust sleep time based on how fast the watcher responds

        // Try to find the file again after it was deleted
        Optional<Resource> deletedResource = fileSystem.find(relativePath);
        assertThat(deletedResource).isNotPresent();
    }
}
