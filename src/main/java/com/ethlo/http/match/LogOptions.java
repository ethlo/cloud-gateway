package com.ethlo.http.match;

import java.util.Optional;

public record LogOptions(Boolean headers, BodyProcessing body)
{
    public LogOptions(final Boolean headers, final BodyProcessing body)
    {
        this.headers = Optional.ofNullable(headers).orElse(true);
        this.body = Optional.ofNullable(body).orElse(BodyProcessing.NONE);
    }

    public enum BodyProcessing
    {
        NONE, SIZE, STORE;

        public boolean mustParse()
        {
            return this == SIZE || this == STORE;
        }
    }
}
