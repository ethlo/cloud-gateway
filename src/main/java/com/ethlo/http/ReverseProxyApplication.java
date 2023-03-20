package com.ethlo.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReverseProxyApplication
{

    public static void main(String[] args)
    {
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
