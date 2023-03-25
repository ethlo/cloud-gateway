package com.ethlo.http.model;

import java.io.InputStream;

public record PayloadProvider(InputStream data, long length)
{

}
