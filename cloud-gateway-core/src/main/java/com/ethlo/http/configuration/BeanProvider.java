package com.ethlo.http.configuration;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class BeanProvider implements ApplicationContextAware
{
    private static ApplicationContext ctx;

    public static <T> T get(Class<T> type)
    {
        return ctx.getBean(type);
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext)
    {
        ctx = applicationContext;
    }
}