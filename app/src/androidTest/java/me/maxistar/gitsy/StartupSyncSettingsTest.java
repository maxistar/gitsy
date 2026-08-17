package me.maxistar.gitsy;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class StartupSyncSettingsTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(
                SharedPreferencesStartupSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.deleteFile("projects.json");
    }

    @After
    public void tearDown() {
        context.getSharedPreferences(
                SharedPreferencesStartupSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void menuChangesSavesAndRestoresModeAndInterval() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openSettingsMenu();
            onView(withId(R.id.startup_sync_mode_if_stale)).check(matches(isChecked()));
            onView(withId(R.id.startup_sync_interval_1_hour)).check(matches(isChecked()));
            onView(withId(R.id.startup_sync_interval_6_hours)).perform(click());
            onView(withText(R.string.save)).perform(click());

            StartupSyncSettings persisted = new SharedPreferencesStartupSyncSettings(context).load();
            assertEquals(StartupSyncMode.IF_STALE, persisted.getMode());
            assertEquals(StartupSyncInterval.SIX_HOURS, persisted.getInterval());

            openSettingsMenu();
            onView(withId(R.id.startup_sync_mode_if_stale)).check(matches(isChecked()));
            onView(withId(R.id.startup_sync_interval_6_hours)).check(matches(isChecked()));
            onView(withId(R.id.startup_sync_interval_1_hour)).check(matches(isNotChecked()));
        }
    }

    @Test
    public void intervalControlsAreDisabledOutsideIfStaleAndManualSyncAllRemainsAvailable() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openSettingsMenu();
            onView(withId(R.id.startup_sync_mode_never)).perform(click());
            onView(withId(R.id.startup_sync_interval_1_hour)).check(matches(not(isEnabled())));
            onView(withText(R.string.save)).perform(click());

            openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.SyncAll)).check(matches(isEnabled()));
        }
    }

    private void openSettingsMenu() {
        openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.startup_sync_settings_menu)).perform(click());
    }
}
