package me.maxistar.gitsy;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

public class NextScheduledSyncCalculatorTest {
    @Test public void choosesSameDayWhenTimeIsAhead() {
        assertNext("2026-09-05T00:00:00Z", "Europe/Berlin", 3, 0,
                "2026-09-05T01:00:00Z");
    }

    @Test public void choosesTomorrowAtExactBoundary() {
        assertNext("2026-09-05T00:00:00Z", "Europe/Berlin", 2, 0,
                "2026-09-06T00:00:00Z");
    }

    @Test public void springGapMovesToFirstValidOffset() {
        assertNext("2026-03-28T23:00:00Z", "Europe/Berlin", 2, 30,
                "2026-03-29T01:30:00Z");
    }

    @Test public void fallOverlapUsesEarlierOffset() {
        assertNext("2026-10-24T23:00:00Z", "Europe/Berlin", 2, 30,
                "2026-10-25T00:30:00Z");
    }

    private void assertNext(String now, String zone, int hour, int minute, String expected) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneId.of("UTC"));
        assertEquals(Instant.parse(expected), new NextScheduledSyncCalculator(
                clock, ZoneId.of(zone)).next(hour, minute));
    }
}
