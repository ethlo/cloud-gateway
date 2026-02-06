package com.ethlo.http.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import com.ethlo.http.MvcPredicateDefinition;
import com.ethlo.http.match.RequestMatchingProcessor;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.predicates.ExtensionPredicateSupplier;
import com.ethlo.http.predicates.NotExtensionPredicateSupplier;
import jakarta.servlet.http.HttpServletRequest;

public class ConfigUtil
{
    /* =======================
       Public API
       ======================= */

    public static List<MvcPredicateDefinition> deserialize(Map<String, Object> matchers)
    {
        if (matchers == null)
        {
            return Collections.emptyList();
        }

        return matchers.values().stream()
                .map(ConfigUtil::toDefinition)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<PredicateConfig> toMatchers(List<RequestMatchingProcessor> matchers)
    {
        return matchers.stream()
                .map(c ->
                {
                    List<MvcPredicateDefinition> defs = deserialize(c.predicates());

                    Predicate<HttpServletRequest> servletPredicate =
                            defs.stream()
                                    .map(ConfigUtil::mapToPredicate)
                                    .reduce(RequestPredicate::and)
                                    .map(ConfigUtil::toServletPredicate)
                                    .orElse(req -> true);

                    return new PredicateConfig(
                            c.id(),
                            servletPredicate,
                            c.request(),
                            c.response()
                    );
                })
                .toList();
    }

    /* =======================
       Parsing
       ======================= */

    private static MvcPredicateDefinition toDefinition(Object value)
    {
        if (value instanceof String str)
        {
            return parseStringShortcut(str);
        }

        if (value instanceof Map<?, ?> map && !map.isEmpty())
        {
            return parseMapPredicate(map);
        }

        return null;
    }

    private static MvcPredicateDefinition parseStringShortcut(String str)
    {
        String[] parts = str.split("=", 2);
        String name = parts[0].trim();

        String raw = parts.length == 2 ? parts[1].trim() : "";
        return new MvcPredicateDefinition(
                name,
                Map.of("pattern", raw)
        );
    }

    private static MvcPredicateDefinition parseMapPredicate(Map<?, ?> outer)
    {
        Map.Entry<?, ?> entry = outer.entrySet().iterator().next();
        String name = entry.getKey().toString();
        Object val = entry.getValue();

        if (val instanceof Map<?, ?> args)
        {
            Map<String, String> stringArgs =
                    args.entrySet().stream()
                            .collect(Collectors.toMap(
                                    e -> e.getKey().toString(),
                                    e -> Objects.toString(e.getValue(), "")
                            ));

            return new MvcPredicateDefinition(name, stringArgs);
        }

        // Map shortcut: Method: GET,POST
        return new MvcPredicateDefinition(
                name,
                Map.of("pattern", Objects.toString(val, ""))
        );
    }

    /* =======================
       Predicate mapping
       ======================= */

    private static RequestPredicate mapToPredicate(MvcPredicateDefinition def)
    {
        String name = def.name().toLowerCase();
        Map<String, String> args = def.args();
        String pattern = args.getOrDefault("pattern", "");

        return switch (name)
        {
            case "path" -> paths(pattern);
            case "notpath" -> paths(pattern).negate();

            case "host" -> hosts(pattern);
            case "nothost" -> hosts(pattern).negate();

            case "method" -> methods(pattern);
            case "notmethod" -> methods(pattern).negate();

            case "extension" ->
                    ExtensionPredicateSupplier.extension(new ExtensionPredicateSupplier.Config().setExtensions(split(pattern)));
            case "notextension" ->
                    NotExtensionPredicateSupplier.notExtension(new ExtensionPredicateSupplier.Config().setExtensions(split(pattern)));

            case "header" -> GatewayRequestPredicates.header(
                    args.get("name"),
                    args.getOrDefault("regexp", ".*")
            );

            default -> req -> true; // forward-compatible
        };
    }

    /* =======================
       Predicate helpers
       ======================= */

    private static RequestPredicate paths(String raw)
    {
        return orList(raw, GatewayRequestPredicates::path);
    }

    private static RequestPredicate hosts(String raw)
    {
        return orList(raw, GatewayRequestPredicates::host);
    }

    private static RequestPredicate methods(String raw)
    {
        return orList(raw, m ->
                GatewayRequestPredicates.method(
                        HttpMethod.valueOf(m.toUpperCase())
                )
        );
    }

    private static RequestPredicate orList(String raw, Function<String, RequestPredicate> mapper)
    {
        List<String> values = split(raw);

        if (values.isEmpty())
        {
            return req -> true;
        }

        return values.stream()
                .map(mapper)
                .reduce(RequestPredicate::or)
                .orElse(req -> true);
    }

    private static List<String> split(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return List.of();
        }

        return Stream.of(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Predicate<HttpServletRequest> toServletPredicate(RequestPredicate rp)
    {
        return servletRequest ->
                rp.test(ServerRequest.create(servletRequest, List.of()));
    }
}
