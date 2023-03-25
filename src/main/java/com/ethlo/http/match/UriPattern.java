package com.ethlo.http.match;

import java.net.URI;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class UriPattern
{
    private final Pattern path;

    public UriPattern(final Pattern path)
    {
        this.path = path;
    }

    public boolean matches(URI uri)
    {
        if (this.path != null)
        {
            return this.path.matcher(uri.getPath()).matches();
        }
        return false;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", "[", "]")
                .add("path=" + path)
                .toString();
    }
}
