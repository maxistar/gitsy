package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NotificationPermissionDecisionTest {
    @Test public void notificationPermissionIsNotRequiredBelowApi33() {
        assertEquals(NotificationPermissionDecision.Result.NOT_REQUIRED,
                NotificationPermissionDecision.evaluate(32, false, false, false));
    }

    @Test public void grantedPermissionWithEnabledNotificationsIsVisible() {
        assertEquals(NotificationPermissionDecision.Result.VISIBLE,
                NotificationPermissionDecision.evaluate(37, true, true, false));
    }

    @Test public void firstMissingPermissionRequestsAccess() {
        assertEquals(NotificationPermissionDecision.Result.REQUEST,
                NotificationPermissionDecision.evaluate(37, false, false, false));
    }

    @Test public void previouslyRequestedOrBlockedAccessIsUnavailable() {
        assertEquals(NotificationPermissionDecision.Result.UNAVAILABLE,
                NotificationPermissionDecision.evaluate(37, false, false, true));
        assertEquals(NotificationPermissionDecision.Result.UNAVAILABLE,
                NotificationPermissionDecision.evaluate(37, true, false, false));
    }
}
