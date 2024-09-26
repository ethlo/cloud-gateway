package com.ethlo.http.util;

import org.springframework.validation.annotation.Validated;

@Validated
public class JavaExpressionConfig
{
    private String expression;
    private String template;

    public String getExpression()
    {
        return expression;
    }

    public void setExpression(final String expression)
    {
        this.expression = expression;
    }

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(final String template)
    {
        this.template = template;
    }
}