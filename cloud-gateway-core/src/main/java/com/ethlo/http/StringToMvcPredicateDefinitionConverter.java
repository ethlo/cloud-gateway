package com.ethlo.http;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class StringToMvcPredicateDefinitionConverter implements Converter<String, MvcPredicateDefinition>
{

    @Override
    public MvcPredicateDefinition convert(String source)
    {
        if (source == null || source.isBlank())
        {
            return null;
        }

        String[] parts = source.split("=", 2);
        String name = parts[0].trim();
        Map<String, String> args = new HashMap<>();

        if (parts.length > 1)
        {
            args.put("_genkey_0", parts[1].trim());
        }

        return new MvcPredicateDefinition(name, args);
    }
}
