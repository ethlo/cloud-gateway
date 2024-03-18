package com.ethlo.http;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.ethlo.http.util.IoUtil;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import org.springframework.core.io.ClassPathResource;

import com.google.common.io.ByteStreams;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.loader.StringLoader;

public class PebbleRenderer
{
    private final PebbleEngine engine;

    public PebbleRenderer(boolean strict)
    {
        final Map<String, Filter> filters = new TreeMap<>();
        filters.put("sizeformat", new Filter()
        {
            @Override
            public Object apply(final Object input, final Map<String, Object> args, final PebbleTemplate self, final EvaluationContext context, final int lineNumber) throws PebbleException
            {
                if (input == null)
                {
                    return null;
                }
                else if (input instanceof Number)
                {
                    return IoUtil.formatSize(((Number) input).longValue());
                }
                return input;
            }

            @Override
            public List<String> getArgumentNames()
            {
                return null;
            }
        });

        engine = new PebbleEngine.Builder()
                .strictVariables(strict)
                .cacheActive(false)
                .loader(new StringLoader())
                .extension(new AbstractExtension()
                {
                    @Override
                    public Map<String, Filter> getFilters()
                    {
                        return filters;
                    }
                }).build();
    }

    public String renderFromTemplate(Map<String, Object> data, String template, Locale locale, boolean disableEscaping) throws PebbleException, IOException
    {
        final String message = new String(ByteStreams.toByteArray(new ClassPathResource(template).getInputStream()), StandardCharsets.UTF_8);
        return render(data, message, locale, disableEscaping);

    }

    public String render(Map<String, Object> data, String message, Locale locale, boolean disableEscaping) throws PebbleException, IOException
    {
        if (disableEscaping)
        {
            message = "{% autoescape false %}" + message + "{% endautoescape %}";
        }

        final StringWriter sw = new StringWriter();
        engine.getTemplate(message).evaluate(sw, data, locale);
        return sw.toString();
    }

    public String render(Map<String, Object> data, String message, Locale locale) throws PebbleException, IOException
    {
        return render(data, message, locale, false);
    }
}
