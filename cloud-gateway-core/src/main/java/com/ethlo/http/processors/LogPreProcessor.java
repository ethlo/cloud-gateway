package com.ethlo.http.processors;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import com.ethlo.http.blocking.model.WebExchangeDataProvider;
import com.ethlo.http.processors.auth.RealmUser;
import com.ethlo.http.processors.auth.extractors.AuthorizationExtractor;
import com.ethlo.http.processors.auth.extractors.DelegateAuthorizationExtractor;

@Component
@RefreshScope
public class LogPreProcessor
{
    private static final Logger logger = LoggerFactory.getLogger(LogPreProcessor.class);

    private final AuthorizationExtractor authorizationExtractor;

    public LogPreProcessor(final DelegateAuthorizationExtractor authorizationExtractor)
    {
        this.authorizationExtractor = authorizationExtractor;
    }

    public WebExchangeDataProvider process(WebExchangeDataProvider data)
    {
        final Optional<RealmUser> realmUser = authorizationExtractor.getUser(data.getRequestHeaders(), data.getResponseHeaders());
        if (logger.isDebugEnabled())
        {
            logger.debug("Extracted user data: {}", realmUser.orElse(null));
        }
        return data.user(realmUser.orElse(null));
    }
}
