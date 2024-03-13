package com.ethlo.http.match;

import java.util.Optional;

public record LogOptions(Boolean headers, ContentProcessing raw, ContentProcessing body)
{
    public LogOptions(final Boolean headers, final ContentProcessing raw, final ContentProcessing body)
    {
        this.headers = Optional.ofNullable(headers).orElse(true);
        this.raw = Optional.ofNullable(raw).orElse(ContentProcessing.NONE);
        this.body = Optional.ofNullable(body).orElse(ContentProcessing.NONE);
    }

    public boolean mustBuffer()
    {
        return raw == ContentProcessing.SIZE
                || raw == ContentProcessing.STORE
                || body == ContentProcessing.SIZE
                || body == ContentProcessing.STORE;
    }

    public enum ContentProcessing
    {
        NONE, SIZE, STORE;
    }
}
