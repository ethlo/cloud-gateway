# Cloud Gateway

Experimental reverse proxy built on top of Spring Cloud Gateway with full request/response header and body logging.

### Logging support
* File - log to file via template pattern for ease of setup.
* ClickHouse - Log to a clickhouse table for powerful and easy analysis.
* JSON - log to JSON files for supporting easy-to-ingest data into 3rd-party storage.


### References

* [Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
* [Cloud LoadBalancer](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
* [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/#configuring-resilience4j-circuit-breakers)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.0.4/reference/htmlsingle/#actuator)

### Guides

* [Using Spring Cloud Gateway](https://github.com/spring-cloud-samples/spring-cloud-gateway-sample)
* [Client-side load-balancing with Spring Cloud LoadBalancer](https://spring.io/guides/gs/spring-cloud-loadbalancer/)

# Special features
If an upstream server is down, the request can still be captured by configuring a fallback for the route.
