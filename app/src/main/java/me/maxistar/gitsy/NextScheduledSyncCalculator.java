package me.maxistar.gitsy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class NextScheduledSyncCalculator {
    private final Clock clock;
    private final ZoneId zoneId;

    NextScheduledSyncCalculator(Clock clock, ZoneId zoneId) {
        this.clock = clock;
        this.zoneId = zoneId;
    }

    Instant next(int hour, int minute) {
        Instant now = clock.instant();
        LocalDate date = now.atZone(zoneId).toLocalDate();
        Instant candidate = resolve(date, hour, minute);
        if (!candidate.isAfter(now)) candidate = resolve(date.plusDays(1), hour, minute);
        return candidate;
    }

    private Instant resolve(LocalDate date, int hour, int minute) {
        LocalDateTime local = LocalDateTime.of(date, LocalTime.of(hour, minute));
        return ZonedDateTime.ofLocal(local, zoneId, null).toInstant();
    }
}
