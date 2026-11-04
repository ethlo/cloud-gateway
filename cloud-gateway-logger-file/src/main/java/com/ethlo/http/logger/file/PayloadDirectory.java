package com.ethlo.http.logger.file;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
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
     * and symbolic link that resolving the path passes through must be owned by root, the owner of <code>/</code> or
     * one of <code>trustedUids</code>, and directories must not be writable by other users unless the sticky bit is
     * set (as for <code>/tmp</code>). Only checked where the unix attribute view is available (Linux, macOS).
     *
     * @param trustedUids The users besides root that may own these directories, typically the user running the gateway
     */
    static Path prepareRoot(final Path root, final Set<Integer> trustedUids, final FileAttribute<?>... attributes) throws IOException
    {
        final Path absolute = root.toAbsolutePath();
        if (!root.getFileSystem().supportedFileAttributeViews().contains("unix"))
        {
            Files.createDirectories(absolute, attributes);
            return absolute.toRealPath();
        }

        // Before creating anything, so nothing is created where the directories could be swapped
        final Resolved existing = resolve(root, absolute, trustedUids);
        Path created = existing.directory();
        for (String name : existing.missing())
        {
            if (name.equals("."))
            {
                continue;
            }
            created = created.resolve(name);
            try
            {
                Files.createDirectory(created, attributes);
            }
            catch (FileAlreadyExistsException e)
            {
                throw new IOException("Refusing to store payloads in " + root + ": " + created + " was created by someone else meanwhile", e);
            }
        }

        final Resolved checked = resolve(root, absolute, trustedUids);
        if (!checked.missing().isEmpty())
        {
            throw new IOException("Refusing to store payloads in " + root + ": " + checked.directory().resolve(checked.missing().getFirst()) + " was removed meanwhile");
        }
        return checked.directory();
    }

    /**
     * @param directory The real path of the part of the path that exists
     * @param missing   The names below it that do not exist yet
     */
    private record Resolved(Path directory, List<String> missing)
    {
    }

    private static final int MAX_LINKS = 40;

    /**
     * Resolves <code>path</code> one name at a time, like the kernel does, checking every directory it passes through
     * and every symbolic link on the way, as the directory holding a link decides where it can point, and a link's
     * owner can repoint it.
     */
    private static Resolved resolve(final Path root, final Path path, final Set<Integer> trustedUids) throws IOException
    {
        final int systemUid = (Integer) Files.getAttribute(path.getRoot(), "unix:uid", LinkOption.NOFOLLOW_LINKS);
        final Deque<String> names = new ArrayDeque<>();
        path.forEach(name -> names.addLast(name.toString()));

        Path current = path.getRoot();
        check(root, current, systemUid, trustedUids);
        int links = 0;
        while (!names.isEmpty())
        {
            final String name = names.removeFirst();
            if (name.equals("."))
            {
                continue;
            }
            if (name.equals(".."))
            {
                // current holds no links, so its parent is where .. leads
                current = current.getParent() != null ? current.getParent() : current;
                continue;
            }

            final Path next = current.resolve(name);
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS))
            {
                names.addFirst(name);
                if (names.contains(".."))
                {
                    throw new IOException("Refusing to store payloads in " + root + ": it goes through " + next + ", which does not exist");
                }
                return new Resolved(current, List.copyOf(names));
            }

            if (check(root, next, systemUid, trustedUids))
            {
                if (++links > MAX_LINKS)
                {
                    throw new IOException("Refusing to store payloads in " + root + ": too many symbolic links");
                }
                final Path target = Files.readSymbolicLink(next);
                if (target.isAbsolute())
                {
                    current = target.getRoot();
                    check(root, current, systemUid, trustedUids);
                }
                final List<String> targetNames = new ArrayList<>();
                target.forEach(n -> targetNames.add(n.toString()));
                for (int i = targetNames.size() - 1; i >= 0; i--)
                {
                    names.addFirst(targetNames.get(i));
                }
                continue;
            }
            current = next;
        }
        return new Resolved(current, List.of());
    }

    /**
     * @return Whether <code>file</code> is a symbolic link
     */
    private static boolean check(final Path root, final Path file, final int systemUid, final Set<Integer> trustedUids) throws IOException
    {
        final Map<String, Object> attributes = Files.readAttributes(file, "unix:uid,mode,isSymbolicLink,isDirectory", LinkOption.NOFOLLOW_LINKS);
        final int uid = (Integer) attributes.get("uid");
        // In a user namespace root's files show up under the overflow uid, so whoever owns / counts as root
        if (uid != ROOT_UID && uid != systemUid && !trustedUids.contains(uid))
        {
            throw new IOException("Refusing to store payloads in " + root + ": " + file + " is owned by uid " + uid + ", not by root or the user running the gateway");
        }

        if ((Boolean) attributes.get("isSymbolicLink"))
        {
            // A link's own permissions mean nothing, what matters is the directory holding it
            return true;
        }
        if (!(Boolean) attributes.get("isDirectory"))
        {
            throw new IOException("Refusing to store payloads in " + root + ": " + file + " is not a directory");
        }

        final int mode = (Integer) attributes.get("mode");
        if ((mode & GROUP_OR_OTHERS_WRITE) != 0 && (mode & STICKY) == 0)
        {
            throw new IOException("Refusing to store payloads in " + root + ": " + file + " is writable by other users without the sticky bit (" + String.format("%04o", mode & 07777) + ")");
        }
        return false;
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
