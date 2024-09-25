package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.handler.AsyncPredicate;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.match.HeaderPredicate;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.netty.PredicateConfig;

class LoggingFilterServiceTest
{
    @Test
    void mergeFilter()
    {
        final LogOptions requestOptions = new LogOptions(new HeaderPredicate(Set.of("bar"), null), LogOptions.ContentProcessing.NONE, LogOptions.ContentProcessing.NONE);
        final PredicateConfig merged = LoggingFilterService.mergeFilter(new HttpLoggingConfiguration()
                        .setFilter(new LogFilter().setHeaders(new HeaderPredicate(Set.of("foo"), null))),
                new PredicateConfig("my-id", new AsyncPredicate.DefaultAsyncPredicate<>(a -> true),
                        requestOptions, null
                )
        );
        assertThat(merged.request().headers().getIncludes()).containsExactly("foo", "bar");
    }
}