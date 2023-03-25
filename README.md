# Cloud Gateway

Experimental reverse proxy built on top of Spring Cloud Gateway with full request/response (including body) logging.

## 

## Logging

### Matching
The requests can be conditionally logged based on numerous properties of the request, as an example:


### Filtering
Sometimes it is not useful to, for example, not log all headers. This can be achieved as this:
```yaml
logging:
  filter:
    headers:
      request:
        excludes:
          - host
          - user-agent
      response:
        excludes:
          - content-security-policy
          - link
          - server
          - date
```
NOTE: HTTP headers are _not_ case-sensitive!

### Logging support
* File - log to file via template-pattern for ease of setup.
* ClickHouse - Log to a clickhouse table for powerful and easy analysis.
* [TBD] - JSON - log to JSON files for supporting easy-to-ingest data into 3rd-party storage.

### Configure logging provider(s)
```yaml
logging:
  provider:
    file:
      enabled: true
      pattern: '{{gateway_request_id}} {{method}} {{path}} {{request_headers["Content-Length"][0]}} {{status}}'
    clickhouse:
      enabled: true
      url: jdbc:ch://localhost:18123/default?compress=0;async_insert=1,wait_for_async_insert=0
```

## Special features

### Logging of request body when upstream server is down
Normally the request is not (fully) consumed by the load balancer/reverse-proxy/gateway, thus the request contents are lost.
The request can still be captured by configuring a fallback for the route, as described below:

```yaml
spring:
  cloud:
      gateway:
        routes:
        - id: my-upstream-is-down
          uri: ${rewrite.backend.uri:http://localhost/get}
          predicates:
            - Path=/get
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
