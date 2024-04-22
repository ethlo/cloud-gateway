# Cloud Gateway

A Docker-packaged reverse proxy built on top of Spring Cloud Gateway with full request/response (including body) logging.

## Trying it out

<img src="doc/basic_dashboard.png" alt= "Simple overview dashboard">

In the `docker` folder there is a `docker-compose.yaml` example that brings up Grafana on port `3000`, and the gateway
on port `6464`.

NOTE: For more information on how to configure, se the sections below. The main config file is
in `config/application.yaml`

Starting the services:

```shell
docker-compose up -d
```

Generate some traffic:

```shell
curl -uuser:pass http://localhost:6464/get
```

View the results in Grafana by going to http://localhost:3000/. The default username/password is `admin`/`grafana`. Show
the dashboard by picking the dashboard named `HTTP traffic` from the left-hand menu.

## Logging

One of the strong points of this project is the logging and ability to viev and analyze traffic. Below is a quick guide
to configuring logging.

### Logging providers

* File - log to file via template-pattern for ease of setup.
* ClickHouse - Log to a clickhouse table for powerful and easy analysis.

```yaml
http-logging:
  provider:
    file:
      enabled: true
      pattern: '{{gateway_request_id}} {{method}} {{path}} {{request_headers["Content-Length"][0]}} {{status}}'
    clickhouse:
      enabled: true
      url: jdbc:ch://localhost:18123?database=default&async_insert=1,wait_for_async_insert=0
```

NOTE: The file log appender can be configured with the logger name `access_log`.

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

## Custom filters

### InjectBasicAuth
Allows the injection of basic auth credentials before forwarding the request upstream

```yaml
filters:
- name: InjectBasicAuth
  args:
    username: ${SECRET_USERNAME}
    password: ${SECRET_PASSWORD}
```

### NotPath
The opposite of `Path`

### NotHost
The opposite of `Host`

### NotExtension
Without any listed extension, it will skip all URLs ending with an extension.

### RedirectTemplate
It supports regexp named parameters, otherwise you can also use numeric variables like `{{1}}` and `{{2}}`. You also have access to query paramters via `query`

Example shorthand:
```yaml
  filters:
    - TemplateRedirect=/foo/(?<var1>.*)/21/(?<var2>.*),https://example.com/{{var2}}?={{var1}}
```

Example full:
```yaml
  filters:
    - name: TemplateRedirect
      source: /foo/(?<var1>.*)/21/(?<var2>.*)
      target: https://example.com/{{var2}}?={{var1}}
      status: 302 # default is 302
```

## References

* [Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
* [Cloud LoadBalancer](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
* [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/#configuring-resilience4j-circuit-breakers)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.0.4/reference/htmlsingle/#actuator)

## Guides

* [Using Spring Cloud Gateway](https://github.com/spring-cloud-samples/spring-cloud-gateway-sample)
* [Client-side load-balancing with Spring Cloud LoadBalancer](https://spring.io/guides/gs/spring-cloud-loadbalancer/)
