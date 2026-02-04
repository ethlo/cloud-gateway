package com.ethlo.http.logger.slf4j;

import java.util.Map;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;

@Component
public class LegacyFileHttpLoggerFactory extends Slf4jHttpLoggerFactory
{
    private static final Logger logger = LoggerFactory.getLogger(LegacyFileHttpLoggerFactory.class);

    @Override
    public String getName()
    {
        return "file";
    }

    @Override
    public HttpLogger getInstance(final Map<String, Object> configuration, final BiFunction<String, Object, Object> beanRegistration)
    {
        logger.warn("Using legacy 'file' logger name. Please switch to 'slf4j'");
        return super.getInstance(configuration, beanRegistration);
    }
}
