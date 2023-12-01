package com.ethlo.http.handlers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty("features.control-panel-handlers.enabled")
@RestController
public class ControlPanelHandlers
{
    private final ApplicationEventPublisher eventPublisher;

    public ControlPanelHandlers(final ApplicationEventPublisher eventPublisher)
    {
        this.eventPublisher = eventPublisher;
    }

    public void triggerRefreshEvent()
    {
        eventPublisher.publishEvent(new RefreshEvent(this, "RefreshEvent", "Refreshing scope"));
    }

    @PostMapping(path = "/config/refresh")
    public ResponseEntity<Resource> triggerRefresh()
    {
        triggerRefreshEvent();
        return ResponseEntity.ok(null);
    }
}
