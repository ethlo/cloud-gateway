package com.ethlo.http;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import com.ethlo.http.blocking.MvcPredicateDefinition;
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
            return request -> true;
        }

        // Combine all definitions into one RequestPredicate
        RequestPredicate combined = definitions.stream()
                .map(this::mapToPredicate)
                .reduce(RequestPredicate::and)
                .orElse(request -> true);

        // Convert the RequestPredicate test to work on the HttpServletRequest
        return servletRequest -> {
            final ServerRequest serverRequest = ServerRequest.create(servletRequest, List.of());
            return combined.test(serverRequest);
        };
    }

    private RequestPredicate mapToPredicate(MvcPredicateDefinition def)
    {
        final String name = def.name();
        final Map<String, String> args = def.args();

        // MVC Gateway provides a helper that mimics the YAML naming convention
        return switch (name.toLowerCase())
        {
            case "path" -> GatewayRequestPredicates.path(args.get("pattern"));
            case "method" -> GatewayRequestPredicates.method(HttpMethod.valueOf(args.get("method").toUpperCase()));
            case "header" -> GatewayRequestPredicates.header(args.get("header"), args.get("regexp"));
            case "host" -> GatewayRequestPredicates.host(args.get("pattern"));
            case "query" -> GatewayRequestPredicates.query(args.get("param"), args.get("regexp"));

            case "extension" ->
            {
                // In GATHER_LIST mode, arguments usually come in as _gen_0, _gen_1...
                // or we can just parse the comma-separated string if that's how your YAML loads
                List<String> extensions = extractList(args, "extensions");
                yield extensionPredicate(extensions);
            }

            default -> throw new IllegalArgumentException("Unsupported MVC predicate: " + name);
        };
    }

    // Helper to handle the "GATHER_LIST" style arguments from YAML
    private List<String> extractList(Map<String, String> args, String key)
    {
        // If the YAML was "Extension: jpg, png", args might have key "extensions"
        // If it was shortcut style, they might be indexed.
        String val = args.get(key);
        if (val != null)
        {
            return List.of(val.split("\\s*,\\s*"));
        }
        return args.values().stream().toList();
    }

    private RequestPredicate extensionPredicate(List<String> extensions)
    {
        return request -> {
            // request is org.springframework.web.servlet.function.ServerRequest
            final String path = request.path();
            final int lastDot = path.lastIndexOf('.');

            if (lastDot != -1 && lastDot < path.length() - 1)
            {
                if (extensions.isEmpty())
                {
                    return true; // Match any extension if list is empty
                }
                final String ext = path.substring(lastDot + 1);
                return extensions.stream().anyMatch(e -> e.equalsIgnoreCase(ext));
            }
            return false;
        };
    }
}