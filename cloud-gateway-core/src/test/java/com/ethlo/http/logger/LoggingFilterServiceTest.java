package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;

import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.match.HeaderPredicate;
import com.ethlo.http.match.HeaderProcessing;
import com.ethlo.http.match.LogOptions;
import com.ethlo.http.netty.PredicateConfig;

class LoggingFilterServiceTest
{
    @Test
    void mergeIncludes()
    {
        final HeaderPredicate global = new HeaderPredicate(Set.of("bar"), null);
        final HeaderPredicate local = new HeaderPredicate(Set.of("foo"), null);
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getIncludes()).containsOnly("foo", "bar");
    }

    @Test
    void localIncludeOverridesGlobalExclude()
    {
        final HeaderPredicate global = new HeaderPredicate(null, Set.of("bar"));
        final HeaderPredicate local = new HeaderPredicate(Set.of("bar"), null);
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getIncludes()).containsOnly("bar");
        assertThat(merged.getExcludes()).isEmpty();
        assertThat(merged.apply("bar")).isEqualTo(HeaderProcessing.NONE);
    }

    @Test
    void localIncludeOverridesGlobalExcludeWithProcessingInstruction()
    {
        final HeaderPredicate global = new HeaderPredicate(null, Set.of("bar,r"));
        final HeaderPredicate local = new HeaderPredicate(Set.of("bar"), null);
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getExcludes()).isEmpty();
        assertThat(merged.apply("bar")).isEqualTo(HeaderProcessing.NONE);
    }

    @Test
    void localExcludeOverridesGlobalInclude()
    {
        final HeaderPredicate global = new HeaderPredicate(Set.of("bar"), null);
        final HeaderPredicate local = new HeaderPredicate(null, Set.of("bar"));
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getIncludes()).isEmpty();
        assertThat(merged.apply("bar")).isEqualTo(HeaderProcessing.DELETE);
    }

    @Test
    void localExcludeOverridesGlobalIncludeWithProcessingInstruction()
    {
        final HeaderPredicate global = new HeaderPredicate(Set.of("bar,r"), null);
        final HeaderPredicate local = new HeaderPredicate(null, Set.of("bar"));
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getIncludes()).isEmpty();
        assertThat(merged.apply("bar")).isEqualTo(HeaderProcessing.DELETE);
    }

    @Test
    void localIncludeOverridesGlobalIncludeProcessingInstruction()
    {
        final HeaderPredicate global = new HeaderPredicate(Set.of("Authorization,r"), null);
        final HeaderPredicate local = new HeaderPredicate(Set.of("Authorization"), null);
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.apply("Authorization")).isEqualTo(HeaderProcessing.NONE);
    }

    @Test
    void mergeIsCaseInsensitive()
    {
        final HeaderPredicate global = new HeaderPredicate(Set.of("Authorization,r"), null);
        final HeaderPredicate local = new HeaderPredicate(null, Set.of("authorization"));
        final HeaderPredicate merged = LoggingFilterService.mergeHeader(global, local);
        assertThat(merged.getIncludes()).isEmpty();
        assertThat(merged.apply("Authorization")).isEqualTo(HeaderProcessing.DELETE);
    }

    @Test
    void mergeCacheIsInvalidatedOnRefresh()
    {
        final HttpLoggingConfiguration configuration = new HttpLoggingConfiguration();
        configuration.setFilter(new LogFilter().setRequestHeaders(new HeaderPredicate(Set.of("global-before"), null)));
        final LoggingFilterService loggingFilterService = new LoggingFilterService(configuration);

        final PredicateConfig predicateConfig = new PredicateConfig("the-matcher", null,
                new LogOptions(new HeaderPredicate(Set.of("local"), null), null, null),
                new LogOptions(new HeaderPredicate(null, null), null, null));

        assertThat(loggingFilterService.merge(predicateConfig).request().headers().getIncludes()).containsOnly("global-before", "local");

        // The matcher id is stable across a refresh, so the cached merge has to be dropped explicitly
        configuration.setFilter(new LogFilter().setRequestHeaders(new HeaderPredicate(Set.of("global-after"), null)));
        loggingFilterService.onApplicationEvent(new RefreshScopeRefreshedEvent());

        assertThat(loggingFilterService.merge(predicateConfig).request().headers().getIncludes()).containsOnly("global-after", "local");
    }
}
