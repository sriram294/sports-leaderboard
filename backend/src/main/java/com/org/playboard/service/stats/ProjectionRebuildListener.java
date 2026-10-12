package com.org.playboard.service.stats;

import com.org.playboard.config.AsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ProjectionRebuildListener {
    private final ProjectionRebuildService rebuilds;
    ProjectionRebuildListener(ProjectionRebuildService rebuilds) { this.rebuilds = rebuilds; }
    @Async(AsyncConfig.PROJECTION_EXECUTOR)
    @TransactionalEventListener
    public void on(ProjectionRebuildRequested event) { rebuilds.rebuild(event.groupId(), event.periodStart(), event.periodEnd()); }
}
