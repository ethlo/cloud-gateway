package com.ethlo.http.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record PathListItem(PathType type, String path, long size)
{
    public static PathListItem of(final Path base, Path path)
    {
        final Path fullPath = base.resolve(path);
        return new PathListItem(PathType.of(fullPath), path.toString(), sizeOf(fullPath));
    }

    private static long sizeOf(Path path)
    {
        try
        {
            return Files.size(path);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    enum PathType
    {
        FILE, DIRECTORY;

        public static PathType of(Path path)
        {
            if (Files.isDirectory(path))
            {
                return DIRECTORY;
            }
            return FILE;
        }
    }
}
