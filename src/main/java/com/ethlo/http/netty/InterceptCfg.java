package com.ethlo.http.netty;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import reactor.netty.http.server.logging.AccessLog;

@Component
public class InterceptCfg implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory>
{
    @Override
    public void customize(NettyReactiveWebServerFactory factory)
    {
        factory.addServerCustomizers(httpServer -> httpServer.accessLog(true, x -> AccessLog.create("method={}, uri={}", x.method(), x.uri())));
    }
}
