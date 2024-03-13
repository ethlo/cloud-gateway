package com.ethlo.http.model;

import java.io.InputStream;

public record RawProvider(InputStream data, long totalLength)
{

}