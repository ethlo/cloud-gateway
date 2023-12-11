package com.ethlo.http.util;

import org.springframework.validation.annotation.Validated;

@Validated
public class JavaExpressionConfig
{
    private String expression;
    private String template;

    public void setExpression(final String expression)
    {
        this.expression = expression;
    }

    public void setTemplate(final String template)
    {
        this.template = template;
    }

    public String getExpression()
    {
        return expression;
    }

    public String getTemplate()
    {
        return template;
    }
}