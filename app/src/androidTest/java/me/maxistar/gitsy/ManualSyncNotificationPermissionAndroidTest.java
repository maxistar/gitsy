package me.maxistar.gitsy;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ManualSyncNotificationPermissionAndroidTest {
    @Test public void api37DeniedStateUsesUnavailablePath() {
        if (Build.VERSION.SDK_INT < 33) return;
        assertEquals(NotificationPermissionDecision.Result.UNAVAILABLE,
                NotificationPermissionDecision.evaluate(Build.VERSION.SDK_INT,
                        false, false, true));
    }

    @Test public void api37GrantedStateUsesVisiblePath() {
        if (Build.VERSION.SDK_INT < 33) return;
        assertEquals(NotificationPermissionDecision.Result.VISIBLE,
                NotificationPermissionDecision.evaluate(Build.VERSION.SDK_INT,
                        true, true, false));
    }
}
