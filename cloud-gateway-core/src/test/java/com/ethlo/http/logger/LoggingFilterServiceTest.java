package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ethlo.http.match.HeaderPredicate;
import com.ethlo.http.match.HeaderProcessing;

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
}