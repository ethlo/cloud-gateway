package com.ethlo.http;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RoutePredicateLocator
{

    /**
     * Resolves MvcPredicateDefinitions into a single executable Predicate.
     */
    public Predicate<HttpServletRequest> getPredicates(List<MvcPredicateDefinition> definitions)
    {
        if (definitions == null || definitions.isEmpty())
        {
            return req -> true;
        }

        // Combine all definitions into one RequestPredicate
        RequestPredicate combined = definitions.stream()
                .map(this::mapToPredicate)
                .reduce(RequestPredicate::and)
                .orElse(req -> true);

        // Wrap RequestPredicate to work on HttpServletRequest
        return servletRequest -> {
            ServerRequest sr = ServerRequest.create(servletRequest, List.of());
            return combined.test(sr);
        };
    }

    private RequestPredicate mapToPredicate(MvcPredicateDefinition def)
    {
        String name = def.name().toLowerCase();
        Map<String, String> args = def.args();

        return switch (name)
        {
            case "method" -> GatewayRequestPredicates.method(HttpMethod.valueOf(getArg(args, "method").toUpperCase()));
            case "notmethod" ->
                    GatewayRequestPredicates.method(HttpMethod.valueOf(getArg(args, "method").toUpperCase())).negate();
            case "path" -> GatewayRequestPredicates.path(getArg(args, "pattern"));
            case "notpath" -> GatewayRequestPredicates.path(getArg(args, "pattern")).negate();

            case "header" -> GatewayRequestPredicates.header(getArg(args, "header"), getArg(args, "regexp"));
            case "host" -> GatewayRequestPredicates.host(getArg(args, "pattern"));
            case "nothost" -> GatewayRequestPredicates.host(getArg(args, "pattern")).negate();
            case "query" -> GatewayRequestPredicates.query(getArg(args, "param"), getArg(args, "regexp"));

            case "extension" -> extensionPredicate(extractList(args, "extensions"));
            case "notextension" -> extensionPredicate(extractList(args, "extensions")).negate();

            default -> throw new IllegalArgumentException("Unsupported MVC predicate: " + def.name());
        };
    }

    /**
     * Returns argument value, fallback to _genkey_0 if missing
     */
    private String getArg(Map<String, String> args, String key)
    {
        String val = args.get(key);
        if (val != null && !val.isBlank())
        {
            return val;
        }
        val = args.get("_genkey_0");
        if (val != null && !val.isBlank())
        {
            return val;
        }
        throw new IllegalArgumentException("Missing required argument '" + key + "' for predicate");
    }

    /**
     * Extract list of values from YAML / shortcut args
     */
    private List<String> extractList(Map<String, String> args, String key)
    {
        String val = args.get(key);
        if (val != null && !val.isBlank())
        {
            return List.of(val.split("\\s*,\\s*"));
        }
        // fallback: take all values (e.g., _gen_0, _gen_1...)
        return args.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .flatMap(v -> Stream.of(v.split("\\s*,\\s*")))
                .collect(Collectors.toList());
    }

    private RequestPredicate extensionPredicate(List<String> extensions)
    {
        return request -> {
            String path = request.path();
            int lastDot = path.lastIndexOf('.');
            if (lastDot != -1 && lastDot < path.length() - 1)
            {
                if (extensions.isEmpty()) return true; // match any
                String ext = path.substring(lastDot + 1);
                return extensions.stream().anyMatch(e -> e.equalsIgnoreCase(ext));
            }
            return false;
        };
    }
}
