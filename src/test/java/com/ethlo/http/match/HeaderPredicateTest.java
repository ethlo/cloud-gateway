package com.ethlo.http.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HeaderPredicateTest
{
    @Test
    void testEmpty()
    {
        final HeaderPredicate matcher = new HeaderPredicate(List.of(), List.of());
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<String> result = input.stream().filter(matcher).toList();
        assertThat(result).containsOnly(input.toArray(new String[0]));
    }
    @Test
    void testIncludes()
    {
        final HeaderPredicate matcher = new HeaderPredicate(List.of("a", "b", "c"), null);
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<String> result = input.stream().filter(matcher).toList();
        assertThat(result).containsOnly(matcher.includes().toArray(new String[0]));
    }

    @Test
    void testExcludes()
    {
        final HeaderPredicate matcher = new HeaderPredicate(null, List.of("a", "b", "c"));
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<String> result = input.stream().filter(matcher).toList();
        assertThat(result).containsOnly("1", "e");
    }

    @Test
    void testIncludesAndExcludes()
    {
        final HeaderPredicate matcher = new HeaderPredicate(List.of("a", "b", "c"), List.of("a"));
        final List<String> input = new ArrayList<>(List.of("1", "e", "a", "c", "b"));
        final List<String> result = input.stream().filter(matcher).toList();
        assertThat(result).containsOnly("b", "c");
    }
}