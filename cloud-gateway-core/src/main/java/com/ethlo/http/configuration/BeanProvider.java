package com.ethlo.http.configuration;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;

public class BeanProvider
{
    private static ApplicationContext ctx;

    public static <T> T get(Class<T> type)
    {
        return ctx.getBean(type);
    }

    public static void setApplicationContext(@NotNull ApplicationContext applicationContext)
    {
        ctx = applicationContext;
    }
}