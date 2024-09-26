package com.ethlo.http.logger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactUtilTest
{
    @Test
    void testRedactNull()
    {
        assertThat(RedactUtil.redact(null)).isNull();
    }

    @Test
    void testRedactEmpty()
    {
        assertThat(RedactUtil.redact("")).isEqualTo("*****");
    }

    @Test
    void testRedactShort()
    {
        assertThat(RedactUtil.redact("abc")).isEqualTo("*****");
    }

    @Test
    void testRedactLong()
    {
        assertThat(RedactUtil.redact("abcdefghijklmno")).isEqualTo("a*****o");
    }
}