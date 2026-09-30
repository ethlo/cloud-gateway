package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ethlo.http.match.QueryParamPredicate;

class QueryStringFilterTest
{
    @Test
    void nullQueryReturnsEmptyMap()
    {
        assertThat(QueryStringFilter.parse(null, new QueryParamPredicate(Set.of("foo")))).isEmpty();
    }

    @Test
    void disabledByDefaultWhenNoAcceptListIsConfigured()
    {
        assertThat(QueryStringFilter.parse("foo=bar", null)).isEmpty();
        assertThat(QueryStringFilter.parse("foo=bar", new QueryParamPredicate(null))).isEmpty();
    }

    @Test
    void onlyAcceptListedParamsAreIncluded()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo"));
        assertThat(QueryStringFilter.parse("foo=bar&api_key=secret", predicate))
                .isEqualTo(Map.of("foo", List.of("bar")));
    }

    @Test
    void acceptListedParamCanStillBeRedacted()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo,r"));
        assertThat(QueryStringFilter.parse("foo=bar", predicate))
                .isEqualTo(Map.of("foo", List.of("*****")));
    }

    @Test
    void allParamsRemovedWhenNoneMatchTheAcceptList()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo"));
        assertThat(QueryStringFilter.parse("api_key=secret", predicate)).isEmpty();
    }

    @Test
    void matchingIsCaseSensitive()
    {
        // Unlike HTTP header names, query parameter names are case-sensitive: an accept-list entry for
        // "page" must not also match "PAGE" or "Page".
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("page"));
        assertThat(QueryStringFilter.parse("page=2&PAGE=secret&Page=other", predicate))
                .isEqualTo(Map.of("page", List.of("2")));
    }

    @Test
    void repeatedParamNameKeepsAllValues()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("tag"));
        assertThat(QueryStringFilter.parse("tag=a&tag=b&api_key=secret", predicate))
                .isEqualTo(Map.of("tag", List.of("a", "b")));
    }
}
