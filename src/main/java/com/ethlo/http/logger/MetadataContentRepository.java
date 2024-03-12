package com.ethlo.http.logger;

import java.util.Map;

public interface MetadataContentRepository
{
    void save(final String requestId, Map<String, Object> metamap);
}
