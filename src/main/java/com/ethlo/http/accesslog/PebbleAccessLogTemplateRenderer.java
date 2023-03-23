package com.ethlo.http.accesslog;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class PebbleAccessLogTemplateRenderer implements AccessLogTemplateRenderer
{
    private final PebbleTemplate compiledTemplate;

    public PebbleAccessLogTemplateRenderer(final String pattern, final boolean strict)
    {
        final PebbleEngine engine = new PebbleEngine.Builder().strictVariables(strict).build();
        this.compiledTemplate = engine.getLiteralTemplate(pattern);
    }

    @Override
    public String render(Map<String, Object> data)
    {
        final Writer writer = new StringWriter();
        try
        {
            compiledTemplate.evaluate(writer, data);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }
}
