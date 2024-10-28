package com.ethlo.http.io;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "file.systems")
public class FileSystemProperties {

    // Map to hold multiple filesystem configurations by a unique key
    private Map<String, List<String>> layers;

    public Map<String, List<String>> getLayers() {
        return layers;
    }

    public void setLayers(Map<String, List<String>> layers) {
        this.layers = layers;
    }
}
