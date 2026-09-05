package me.maxistar.gitsy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

final class ScheduledSyncScheduler {
    private final ScheduledSyncSettingsRepository settings;
    private final WorkManagerGateway gateway;
    private final Clock clock;
    private final ZoneId zoneId;

    ScheduledSyncScheduler(ScheduledSyncSettingsRepository settings, WorkManagerGateway gateway,
                           Clock clock, ZoneId zoneId) {
        this.settings = settings; this.gateway = gateway; this.clock = clock; this.zoneId = zoneId;
    }

    void reconcile() {
        ScheduledSyncSettings value = settings.load();
        if (!value.isEnabled()) { gateway.cancelDailyTrigger(); return; }
        Instant next = new NextScheduledSyncCalculator(clock, zoneId)
                .next(value.getHour(), value.getMinute());
        gateway.replaceDailyTrigger(next);
    }
}
