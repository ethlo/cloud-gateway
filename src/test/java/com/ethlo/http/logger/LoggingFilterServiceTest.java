package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ethlo.http.match.HeaderPredicate;

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
        assertThat(merged.getExcludes()).containsOnly("bar,d");
    }

}