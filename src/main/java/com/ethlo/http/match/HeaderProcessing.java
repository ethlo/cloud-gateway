package com.ethlo.http.match;

public enum HeaderProcessing
{
    NONE(""), REDACT("r"), DELETE("d");

    private final String id;

    HeaderProcessing(String id)
    {
        this.id = id;
    }

    public String getId()
    {
        return id;
    }
}
