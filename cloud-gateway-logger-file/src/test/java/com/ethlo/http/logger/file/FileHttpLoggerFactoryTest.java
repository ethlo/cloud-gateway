package com.ethlo.http.logger.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ethlo.http.logger.HttpLogger;

class FileHttpLoggerFactoryTest
{
    @Test
    void bindsStorageDirectory()
    {
        final HttpLogger logger = new FileHttpLoggerFactory().getInstance(null, Map.of("enabled", "true", "pattern", "{{status}}", "storage-directory", "/var/log/payloads"), (name, bean) -> null);
        assertThat(logger.toString()).contains("storageDirectory=/var/log/payloads");
    }

    @Test
    void storageDirectoryIsOptional()
    {
        final HttpLogger logger = new FileHttpLoggerFactory().getInstance(null, Map.of("enabled", "true", "pattern", "{{status}}"), (name, bean) -> null);
        assertThat(logger.toString()).contains("storageDirectory=null");
    }
}
