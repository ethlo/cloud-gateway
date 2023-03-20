package com.ethlo.http;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import org.springframework.context.annotation.Configuration;

import reactor.netty.http.server.logging.AccessLog;

@Configuration
public class AccessLogCfg implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory>
{
    @Override
    public void customize(NettyReactiveWebServerFactory factory)
    {
        factory.addServerCustomizers(httpServer -> httpServer.accessLog(true, x -> AccessLog.create("request_id={}, timestamp={}, method={}, uri={}, status={}", x.accessDateTime(), x.method(), x.uri(), x.status())));
    }
}