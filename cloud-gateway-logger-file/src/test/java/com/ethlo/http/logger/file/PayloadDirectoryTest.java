package com.ethlo.http.logger.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PayloadDirectoryTest
{
    @TempDir
    private Path root;

    @TempDir
    private Path elsewhere;

    @Test
    void operationsStayInTheOpenedDirectoryWhenItsPathIsReplacedWithASymlink() throws IOException
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root))
        {
            assumeTrue(stream instanceof SecureDirectoryStream, "No SecureDirectoryStream on this file system");
        }

        final Path day = Files.createDirectory(root.resolve("2026-11-04"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        final Path moved = root.resolve("moved");

        try (PayloadDirectory directory = PayloadDirectory.open(day, Files.getOwner(root)))
        {
            // Swap the checked path for a symlink to somewhere else after it was opened
            Files.move(day, moved);
            Files.createSymbolicLink(day, elsewhere);

            try (SeekableByteChannel channel = directory.create("payload.tmp"))
            {
                channel.write(ByteBuffer.wrap("data".getBytes(StandardCharsets.UTF_8)));
            }
            directory.rename("payload.tmp", "payload");
            try (SeekableByteChannel channel = directory.create("other.tmp"))
            {
                channel.write(ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)));
            }
            directory.delete("other.tmp");
        }

        assertThat(elsewhere).isEmptyDirectory();
        assertThat(moved.resolve("payload")).hasContent("data");
        assertThat(moved).isDirectoryContaining(p -> p.getFileName().toString().equals("payload"))
                .isDirectoryNotContaining(p -> p.getFileName().toString().endsWith(".tmp"));
    }
}
