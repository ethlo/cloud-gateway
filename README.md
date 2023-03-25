# Cloud Gateway

Experimental reverse proxy built on top of Spring Cloud Gateway with full request/response (including body) logging.

##  

## Logging

### Logging support

* File - log to file via template-pattern for ease of setup.
* ClickHouse - Log to a clickhouse table for powerful and easy analysis.

### Logging provider(s)

```yaml
http-logging:
  provider:
    file:
      enabled: true
      pattern: '{{gateway_request_id}} {{method}} {{path}} {{request_headers["Content-Length"][0]}} {{status}}'
    clickhouse:
      enabled: true
      url: jdbc:ch://localhost:18123/default?compress=0;async_insert=1,wait_for_async_insert=0
```
NOTE: The file log appender can be configured with the logger name `access_log`.

### Capture configuration
The requests can be conditionally logged based on numerous properties of the request. The capturing is happening straight from the bytebuffers in Netty, and is attempted to be done in a manner that incurs minimum overhead. The `memory-buffer-size` defines how large the request or response can be before it is buffered to file.

```yaml
capture:
    memory-buffer-size: 500KB
    temp-directory: /tmp
  matchers:
    - includes:
        - uris:
            - path: /my-service
          methods:
            - GET
            - POST
      log-request-body: true
      log-response-body: true
    - includes:
      - uris:
          - path: /my-other-service
      log-request-body: true
      log-response-body: false # default is also false for request/response body logging
```
 NOTE: Keep in mind that the request and response body logging may be invaluable for debugging and auditing, it also potentially affects the performance negatively, as this will require additional resources for both processing and storage.

## Special features

### Logging of request body when upstream server is down

Normally the request is not (fully) consumed by the load balancer/reverse-proxy/gateway, thus the request contents are
lost. The request can still be captured by configuring a fallback for the route, as described below:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: my-upstream-is-down
          uri: ${rewrite.backend.uri:http://my-service1}
          predicates:
            - Path=/my-service
          filters:
           - name: CircuitBreaker
             args:
               name: upstream-down
               fallbackUri: forward:/upstream-down
```

## References

* [Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
* [Cloud LoadBalancer](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
* [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/#configuring-resilience4j-circuit-breakers)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.0.4/reference/htmlsingle/#actuator)

## Guides

* [Using Spring Cloud Gateway](https://github.com/spring-cloud-samples/spring-cloud-gateway-sample)
* [Client-side load-balancing with Spring Cloud LoadBalancer](https://spring.io/guides/gs/spring-cloud-loadbalancer/)
