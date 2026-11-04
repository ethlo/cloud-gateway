package com.ethlo.http.logger.file;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

/**
 * A day directory that payloads are written into. Where the file system supports it, the directory is opened once
 * without following symbolic links, checked through that handle, and every file operation is relative to it, so the
 * directory that was checked is the one written to even if the path is swapped meanwhile.
 */
final class PayloadDirectory implements Closeable
{
    private final Path directory;
    private final SecureDirectoryStream<Path> stream;

    private PayloadDirectory(final Path directory, final SecureDirectoryStream<Path> stream)
    {
        this.directory = directory;
        this.stream = stream;
    }

    /**
     * @param expectedOwner The user the directory must belong to, <code>null</code> where ownership is not available
     */
    static PayloadDirectory open(final Path directory, final UserPrincipal expectedOwner) throws IOException
    {
        try (DirectoryStream<Path> parent = Files.newDirectoryStream(directory.getParent()))
        {
            if (parent instanceof SecureDirectoryStream<Path> secureParent)
            {
                final SecureDirectoryStream<Path> stream;
                try
                {
                    stream = secureParent.newDirectoryStream(directory.getFileName(), LinkOption.NOFOLLOW_LINKS);
                }
                catch (FileSystemLoopException e)
                {
                    throw new IOException("Refusing to store payloads in " + directory + ": it is a symbolic link", e);
                }

                try
                {
                    final PosixFileAttributeView view = stream.getFileAttributeView(PosixFileAttributeView.class);
                    if (view != null)
                    {
                        requireSafe(directory, view.readAttributes(), expectedOwner);
                    }
                    return new PayloadDirectory(directory, stream);
                }
                catch (IOException | RuntimeException e)
                {
                    stream.close();
                    throw e;
                }
            }
        }

        // No handle based access on this file system, so check by path as well as possible
        if (Files.isSymbolicLink(directory))
        {
            throw new IOException("Refusing to store payloads in " + directory + ": it is a symbolic link");
        }
        final PosixFileAttributeView view = Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view != null)
        {
            requireSafe(directory, view.readAttributes(), expectedOwner);
        }
        return new PayloadDirectory(directory, null);
    }

    private static void requireSafe(final Path directory, final PosixFileAttributes attributes, final UserPrincipal expectedOwner) throws IOException
    {
        if (expectedOwner != null && !expectedOwner.equals(attributes.owner()))
        {
            throw new IOException("Refusing to store payloads in " + directory + ": it is owned by " + attributes.owner().getName() + ", not by " + expectedOwner.getName());
        }

        final Set<PosixFilePermission> permissions = attributes.permissions();
        if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE))
        {
            throw new IOException("Refusing to store payloads in " + directory + ": it is writable by other users (" + PosixFilePermissions.toString(permissions) + ")");
        }
    }

    /**
     * Create a new file, failing if anything, including a symbolic link, already has that name.
     */
    SeekableByteChannel create(final String name, final FileAttribute<?>... attributes) throws IOException
    {
        final Set<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return stream != null
                ? stream.newByteChannel(Path.of(name), options, attributes)
                : Files.newByteChannel(directory.resolve(name), options, attributes);
    }

    void rename(final String source, final String target) throws IOException
    {
        if (stream != null)
        {
            stream.move(Path.of(source), stream, Path.of(target));
        }
        else
        {
            Files.move(directory.resolve(source), directory.resolve(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    void delete(final String name) throws IOException
    {
        if (stream != null)
        {
            stream.deleteFile(Path.of(name));
        }
        else
        {
            Files.delete(directory.resolve(name));
        }
    }

    @Override
    public void close() throws IOException
    {
        if (stream != null)
        {
            stream.close();
        }
    }
}
