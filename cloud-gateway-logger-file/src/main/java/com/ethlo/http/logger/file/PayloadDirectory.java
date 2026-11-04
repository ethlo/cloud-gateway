package com.ethlo.http.logger.file;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
                catch (FileSystemException e)
                {
                    // Opening a symbolic link without following it fails with ELOOP, reported differently across JDKs
                    if (Files.isSymbolicLink(directory))
                    {
                        throw new IOException("Refusing to store payloads in " + directory + ": it is a symbolic link", e);
                    }
                    throw e;
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

    /**
     * The owner of a file created in <code>root</code>, which is who the files the gateway creates there belong to.
     * Created and inspected through a handle to <code>root</code> where supported.
     */
    static UserPrincipal probeOwner(final Path root, final FileAttribute<?>... attributes) throws IOException
    {
        Files.createDirectories(root);
        final Path probe = Path.of(".owner-probe-" + UUID.randomUUID() + ".tmp");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root))
        {
            if (stream instanceof SecureDirectoryStream<Path> secure)
            {
                secure.newByteChannel(probe, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), attributes).close();
                try
                {
                    return secure.getFileAttributeView(probe, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes().owner();
                }
                finally
                {
                    secure.deleteFile(probe);
                }
            }
        }

        final Path file = Files.createFile(root.resolve(probe), attributes);
        try
        {
            return Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
        }
        finally
        {
            Files.delete(file);
        }
    }

    private static final int GROUP_OR_OTHERS_WRITE = 0022;
    private static final int STICKY = 01000;
    private static final int ROOT_UID = 0;

    /**
     * Create the storage directory if needed and return its real path, after making sure nobody but trusted users can
     * swap it, or entries in it, as they could then tamper with the owner probe or the day directories. Every directory
     * on the configured path and on the resolved path must not be writable by other users unless the sticky bit is set
     * (as for <code>/tmp</code>), and must be owned by root, the owner of <code>/</code>, one of
     * <code>trustedUids</code> or the owner of the files the gateway creates there. Only checked where the unix
     * attribute view is available (Linux, macOS).
     *
     * @param trustedUids The users besides root that may own these directories, typically the user running the gateway
     * @param ownerProbe  Finds who owns the files the gateway creates in the (real) storage directory
     */
    static Path prepareRoot(final Path root, final Set<Integer> trustedUids, final OwnerProbe ownerProbe, final FileAttribute<?>... attributes) throws IOException
    {
        final Path absolute = root.toAbsolutePath().normalize();
        final boolean unix = root.getFileSystem().supportedFileAttributeViews().contains("unix");
        if (unix)
        {
            Path existing = absolute;
            while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS))
            {
                existing = existing.getParent();
            }
            // Permissions first, so nothing is created where the directories could be swapped
            requireSafePath(root, existing, null, null, false);
        }

        Files.createDirectories(absolute, attributes);
        final Path real = absolute.toRealPath();
        if (unix)
        {
            final UserPrincipal owner = ownerProbe.probe(real);
            requireSafePath(root, absolute, trustedUids, owner, true);
            requireSafePath(root, real, trustedUids, owner, true);
            if (!absolute.toRealPath().equals(real))
            {
                throw new IOException("Refusing to store payloads in " + root + ": it no longer resolves to " + real);
            }
        }
        return real;
    }

    @FunctionalInterface
    interface OwnerProbe
    {
        UserPrincipal probe(Path directory) throws IOException;
    }

    /**
     * Checks <code>path</code> and every directory above it, plus the real path of any symbolic link on the way, as the
     * directory holding a link decides where it can point.
     */
    private static void requireSafePath(final Path root, final Path path, final Set<Integer> trustedUids, final UserPrincipal trustedOwner, final boolean checkOwners) throws IOException
    {
        final int systemUid = (Integer) Files.getAttribute(path.getRoot(), "unix:uid", LinkOption.NOFOLLOW_LINKS);
        for (Path directory = path; directory != null; directory = directory.getParent())
        {
            final Map<String, Object> attributes = Files.readAttributes(directory, "unix:uid,owner,mode,isSymbolicLink", LinkOption.NOFOLLOW_LINKS);
            final int uid = (Integer) attributes.get("uid");
            // In a user namespace root's files show up under the overflow uid, so whoever owns / counts as root
            if (checkOwners && uid != ROOT_UID && uid != systemUid && !trustedUids.contains(uid) && !attributes.get("owner").equals(trustedOwner))
            {
                throw new IOException("Refusing to store payloads in " + root + ": " + directory + " is owned by uid " + uid + ", not by root or the user running the gateway");
            }

            if ((Boolean) attributes.get("isSymbolicLink"))
            {
                // A link's own permissions mean nothing; its parent is checked next, and its target here
                requireSafePath(root, directory.toRealPath(), trustedUids, trustedOwner, checkOwners);
                continue;
            }

            final int mode = (Integer) attributes.get("mode");
            if ((mode & GROUP_OR_OTHERS_WRITE) != 0 && (mode & STICKY) == 0)
            {
                throw new IOException("Refusing to store payloads in " + root + ": " + directory + " is writable by other users without the sticky bit (" + String.format("%04o", mode & 07777) + ")");
            }
        }
    }

    /**
     * The uid of the user running the gateway, or empty where it cannot be determined. Taken from the owner of
     * <code>/proc/self</code> on Linux, else from the JDK's <code>UnixSystem</code>.
     */
    static Optional<Integer> processUid()
    {
        try
        {
            final Path self = Path.of("/proc/self");
            if (Files.isDirectory(self))
            {
                return Optional.of((Integer) Files.getAttribute(self, "unix:uid"));
            }
        }
        catch (IOException | RuntimeException ignored)
        {
            // Fall through to UnixSystem
        }

        try
        {
            final Object unixSystem = Class.forName("com.sun.security.auth.module.UnixSystem").getConstructor().newInstance();
            return Optional.of(((Number) unixSystem.getClass().getMethod("getUid").invoke(unixSystem)).intValue());
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            return Optional.empty();
        }
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
