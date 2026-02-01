package com.ethlo.http.blocking;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationPropertiesBinding
public class StringToMvcPredicateDefinitionConverter implements Converter<String, MvcPredicateDefinition> {

    @Override
    public MvcPredicateDefinition convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        // Handle shortcut format like "Path=/api/**" or "Method=GET"
        String[] parts = source.split("=", 2);
        String name = parts[0];
        Map<String, String> args = new HashMap<>();

        if (parts.length > 1) {
            String argValue = parts[1];
            // Basic shortcut mapping: 
            // Most Gateway predicates use a default arg name 
            // Path uses "pattern", Method uses "method", etc.
            String argKey = switch (name.toLowerCase()) {
                case "path" -> "pattern";
                case "method" -> "method";
                case "host" -> "pattern";
                case "header" -> "header"; // Note: headers usually need name,regexp
                default -> "value"; 
            };
            args.put(argKey, argValue);
        }

        return new MvcPredicateDefinition(name, args);
    }
}