package com.ethlo.http.match;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.NONE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

class HeaderPredicateTest
{
    @Test
    void testEmpty()
    {
        final HeaderPredicate matcher = new HeaderPredicate(null, null);
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<HeaderProcessing> result = input.stream().map(matcher).toList();
        assertThat(result).containsExactly(NONE, NONE, NONE, NONE, NONE);
    }

    @Test
    void testIncludes()
    {
        final HeaderPredicate matcher = new HeaderPredicate(Set.of("a", "b,r", "c"), null);
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<HeaderProcessing> result = input.stream().map(matcher).toList();
        assertThat(result).containsExactly(DELETE, DELETE, NONE, NONE, REDACT);
    }

    @Test
    void testExcludes()
    {
        final HeaderPredicate matcher = new HeaderPredicate(null, Set.of("a", "b,r", "c"));
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<HeaderProcessing> result = input.stream().map(matcher).toList();
        assertThat(result).containsExactly(NONE, NONE, DELETE, DELETE, REDACT);
    }

    @Test
    void testIncludesAndExcludes()
    {
        final IllegalArgumentException exc = Assert.assertThrows(IllegalArgumentException.class, () -> new HeaderPredicate(Set.of("a", "b", "c"), Set.of("a")));
        assertThat(exc.getMessage()).isEqualTo("Cannot have both includes and excludes");
    }
}