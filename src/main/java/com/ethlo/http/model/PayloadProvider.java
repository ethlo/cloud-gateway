package com.ethlo.http.model;

import java.io.InputStream;

public record PayloadProvider(InputStream data, Long bodyLength, long totalLength)
{

}
