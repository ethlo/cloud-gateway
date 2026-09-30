package com.ethlo.http.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ethlo.http.match.HeaderPredicate;

class QueryStringFilterTest
{
    @Test
    void nullQueryIsUnchanged()
    {
        assertThat(QueryStringFilter.filter(null, new HeaderPredicate(Set.of("foo"), null))).isNull();
    }

    @Test
    void disabledByDefaultWhenNoAcceptListIsConfigured()
    {
        assertThat(QueryStringFilter.filter("foo=bar", null)).isNull();
        assertThat(QueryStringFilter.filter("foo=bar", new HeaderPredicate(null, null))).isNull();
    }

    @Test
    void onlyAcceptListedParamsArePassedThrough()
    {
        final HeaderPredicate predicate = new HeaderPredicate(Set.of("foo"), null);
        assertThat(QueryStringFilter.filter("foo=bar&api_key=secret", predicate)).isEqualTo("foo=bar");
    }

    @Test
    void acceptListedParamCanStillBeRedacted()
    {
        final HeaderPredicate predicate = new HeaderPredicate(Set.of("foo,r"), null);
        assertThat(QueryStringFilter.filter("foo=bar", predicate)).isEqualTo("foo=*****");
    }

    @Test
    void allParamsRemovedWhenNoneMatchTheAcceptList()
    {
        final HeaderPredicate predicate = new HeaderPredicate(Set.of("foo"), null);
        assertThat(QueryStringFilter.filter("api_key=secret", predicate)).isNull();
    }
}
