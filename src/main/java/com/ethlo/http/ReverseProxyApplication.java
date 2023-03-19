package com.ethlo.http;

import io.netty.util.internal.logging.AbstractInternalLogger;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4JLoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReverseProxyApplication {

    public static void main(String[] args) {
        /*InternalLoggerFactory.setDefaultFactory(new InternalLoggerFactory(){
            @Override
            protected InternalLogger newInstance(final String name)
            {
                return new AbstractInternalLogger()
                {
                };
            }
        });
        */
        SpringApplication.run(ReverseProxyApplication.class, args);
    }
}
