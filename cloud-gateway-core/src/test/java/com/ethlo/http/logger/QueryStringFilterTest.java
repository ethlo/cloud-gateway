package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ethlo.http.match.QueryParamPredicate;

class QueryStringFilterTest
{
    @Test
    void nullQueryIsUnchanged()
    {
        assertThat(QueryStringFilter.filter(null, new QueryParamPredicate(Set.of("foo"), null))).isNull();
    }

    @Test
    void disabledByDefaultWhenNoAcceptListIsConfigured()
    {
        assertThat(QueryStringFilter.filter("foo=bar", null)).isNull();
        assertThat(QueryStringFilter.filter("foo=bar", new QueryParamPredicate(null, null))).isNull();
    }

    @Test
    void onlyAcceptListedParamsArePassedThrough()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo"), null);
        assertThat(QueryStringFilter.filter("foo=bar&api_key=secret", predicate)).isEqualTo("foo=bar");
    }

    @Test
    void acceptListedParamCanStillBeRedacted()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo,r"), null);
        assertThat(QueryStringFilter.filter("foo=bar", predicate)).isEqualTo("foo=*****");
    }

    @Test
    void allParamsRemovedWhenNoneMatchTheAcceptList()
    {
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("foo"), null);
        assertThat(QueryStringFilter.filter("api_key=secret", predicate)).isNull();
    }

    @Test
    void matchingIsCaseSensitive()
    {
        // Unlike HTTP header names, query parameter names are case-sensitive: an accept-list entry for
        // "page" must not also match "PAGE" or "Page".
        final QueryParamPredicate predicate = new QueryParamPredicate(Set.of("page"), null);
        assertThat(QueryStringFilter.filter("page=2&PAGE=secret&Page=other", predicate)).isEqualTo("page=2");
    }
}
