package com.ethlo.http.model;

import java.io.InputStream;

public record BodyProvider(InputStream data, long bodyLength)
{

}
