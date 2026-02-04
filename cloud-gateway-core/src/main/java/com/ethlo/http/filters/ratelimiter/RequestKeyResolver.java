package com.ethlo.http.filters.ratelimiter;

import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.function.ServerRequest;

public interface RequestKeyResolver extends Function<@NonNull ServerRequest, String>
{
}
