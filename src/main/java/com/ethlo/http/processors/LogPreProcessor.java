package com.ethlo.http.processors;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.processors.auth.AuthorizationExtractor;
import com.ethlo.http.processors.auth.RealmUser;

@Component
public class LogPreProcessor
{
    private static final Logger logger = LoggerFactory.getLogger(LogPreProcessor.class);

    private final AuthorizationExtractor authorizationExtractor;

    public LogPreProcessor(final AuthorizationExtractor authorizationExtractor)
    {
        this.authorizationExtractor = authorizationExtractor;
    }

    public WebExchangeDataProvider process(WebExchangeDataProvider data)
    {
        final Optional<RealmUser> realmUser = authorizationExtractor.getUser(data.getRequestHeaders());
        if (logger.isDebugEnabled())
        {
            logger.debug("Extracted user data: {}", realmUser.orElse(null));
        }
        data.user(realmUser.orElse(null));
        return data;
    }
}
