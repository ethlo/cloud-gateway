package com.ethlo.http.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record PathListItem(PathType type, String path, long size)
{
    public static PathListItem of(final Path path)
    {
        return new PathListItem(PathType.of(path), path.getFileName().toString(), sizeOf(path));
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
        FILE, DIRECTORY, ROOT;

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
