package com.ethlo.http;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ethlo.http.util.ObservableScheduler;

@Component
@ConditionalOnProperty("http-logging.capture.enabled")
@Endpoint(id = "logio")
public class IOStatusEndpoint
{
    private final ObservableScheduler observableScheduler;

    public IOStatusEndpoint(ObservableScheduler observableScheduler)
    {
        this.observableScheduler = observableScheduler;
    }

    @ReadOperation
    public Map<String, Object> getLogio()
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("queue_length", observableScheduler.getQueueSize());
        result.put("queue_capacity", observableScheduler.getQueueCapacity());
        result.put("queue_waits", observableScheduler.getQueueInsertDelayCount());
        result.put("queue_wait_duration", observableScheduler.getQueueInsertDelayElapsed().toMillis());
        return result;
    }
}