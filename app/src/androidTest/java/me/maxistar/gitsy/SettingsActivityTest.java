package me.maxistar.gitsy;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.content.res.Configuration;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences().edit().clear().commit();
        scheduledPreferences().edit().clear().commit();
        context.deleteFile("projects.json");
        ServiceLocator.getInstance().replaceStartupSyncSessionForTests(new StartupSyncSession());
    }

    @After
    public void tearDown() {
        preferences().edit().clear().commit();
        scheduledPreferences().edit().clear().commit();
    }

    @Test
    public void menuOpensUnifiedSettingsAndChangesPersistImmediately() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.settings_title)).perform(click());
            onView(withId(R.id.settings_sync_section)).check(matches(isDisplayed()));
            onView(withId(R.id.settings_about_section)).perform(scrollTo()).check(matches(isDisplayed()));

            onView(withId(R.id.startup_sync_mode_ask_if_stale)).perform(click());
            assertEquals(StartupSyncMode.ASK_IF_STALE, repository().load().getMode());
            onView(withId(R.id.startup_sync_interval_6_hours)).perform(click());
            assertEquals(StartupSyncInterval.SIX_HOURS, repository().load().getInterval());

            pressBack();
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.settings_title)).perform(click());
            onView(withId(R.id.startup_sync_mode_ask_if_stale)).check(matches(isChecked()));
            onView(withId(R.id.startup_sync_interval_6_hours)).check(matches(isChecked()));
        }
    }

    @Test
    public void intervalControlsTrackModeAndAboutRowsArePresent() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.startup_sync_mode_never)).perform(click());
            onView(withId(R.id.startup_sync_interval_1_hour)).check(matches(not(isEnabled())));
            onView(withId(R.id.startup_sync_mode_if_stale)).perform(click());
            onView(withId(R.id.startup_sync_interval_1_hour)).check(matches(isEnabled()));
            onView(withId(R.id.settings_about_version)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.settings_website_row)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.settings_documentation_row)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.settings_terms_row)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void scheduledDefaultsAreDisabledAtTwoAndStartupRemainsIndependent() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.scheduled_sync_enabled)).perform(scrollTo())
                    .check(matches(not(isChecked())));
            onView(withId(R.id.scheduled_sync_time)).check(matches(isDisplayed()));
            ScheduledSyncSettings scheduled = new SharedPreferencesScheduledSyncSettings(context).load();
            assertFalse(scheduled.isEnabled());
            assertEquals(2, scheduled.getHour());
            assertEquals(0, scheduled.getMinute());
            assertEquals(StartupSyncSettings.DEFAULT.getMode(), repository().load().getMode());
            assertEquals(StartupSyncSettings.DEFAULT.getInterval(), repository().load().getInterval());
        }
    }

    @Test public void storedScheduledSettingSurvivesActivityRecreation() {
        new SharedPreferencesScheduledSyncSettings(context)
                .save(new ScheduledSyncSettings(true, 4, 15));
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.scheduled_sync_enabled)).perform(scrollTo())
                    .check(matches(isChecked()));
            onView(withId(R.id.scheduled_sync_next)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withId(R.id.scheduled_sync_enabled)).perform(scrollTo())
                    .check(matches(isChecked()));
            ScheduledSyncSettings scheduled = new SharedPreferencesScheduledSyncSettings(context).load();
            assertEquals(4, scheduled.getHour());
            assertEquals(15, scheduled.getMinute());
        }
    }

    @Test public void selectedTimePersistsAndSurvivesRecreation() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> activity.updateScheduledTime(5, 35));
            ScheduledSyncSettings value = new SharedPreferencesScheduledSyncSettings(context).load();
            assertEquals(5, value.getHour()); assertEquals(35, value.getMinute());
            scenario.recreate();
            value = new SharedPreferencesScheduledSyncSettings(context).load();
            assertEquals(5, value.getHour()); assertEquals(35, value.getMinute());
        }
    }

    @Test public void scheduledSettingEnablesAndDisablesImmediately() {
        grantNotifications();
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.scheduled_sync_enabled)).perform(scrollTo(), click());
            assertTrue(new SharedPreferencesScheduledSyncSettings(context).load().isEnabled());
            onView(withId(R.id.scheduled_sync_next)).check(matches(isDisplayed()));
            assertEquals(StartupSyncSettings.DEFAULT.getMode(), repository().load().getMode());

            onView(withId(R.id.scheduled_sync_enabled)).perform(click());
            assertFalse(new SharedPreferencesScheduledSyncSettings(context).load().isEnabled());
            onView(withId(R.id.scheduled_sync_next)).check(matches(not(isDisplayed())));
        }
    }

    @Test public void deniedNotificationStateIsExplainedWithoutDisablingSchedule() {
        if (Build.VERSION.SDK_INT < 33) return;
        new SharedPreferencesScheduledSyncSettings(context)
                .save(new ScheduledSyncSettings(true, 2, 0));
        appOpNotifications("ignore");
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.scheduled_sync_notification_guidance)).perform(scrollTo())
                    .check(matches(isDisplayed()));
            assertTrue(new SharedPreferencesScheduledSyncSettings(context).load().isEnabled());
        } finally {
            appOpNotifications("allow");
        }
    }

    @Test public void scheduledCopyExistsInEverySupportedLocale() {
        for (String language : new String[]{"en", "de", "it", "ru"}) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(Locale.forLanguageTag(language));
            Context localized = context.createConfigurationContext(configuration);
            assertFalse(localized.getString(R.string.scheduled_sync_settings_title).isEmpty());
            assertFalse(localized.getString(R.string.scheduled_sync_explanation).isEmpty());
            assertFalse(localized.getString(R.string.scheduled_sync_notification_denied).isEmpty());
            assertFalse(localized.getString(R.string.scheduled_sync_running).isEmpty());
            assertFalse(localized.getString(R.string.scheduled_sync_failure_attention).isEmpty());
        }
    }

    @Test
    public void backClosesSettingsAndMissingLinkHandlerDoesNotCrash() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> activity.openExternalUrl(""));
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            pressBackUnconditionally();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotEquals(Lifecycle.State.RESUMED, scenario.getState());
        }
    }

    @Test
    public void sshKeyImportReplacementWrongPassphraseAndRecoveryUseAppPrivateCopy() throws Exception {
        ValueEncryptor encryptor = ServiceLocator.getInstance().getValueEncryptor();
        EncryptedFileSshIdentityRepository identities =
                new EncryptedFileSshIdentityRepository(context, encryptor);
        EncryptedFileSshPassphraseRepository passphrases =
                new EncryptedFileSshPassphraseRepository(context, encryptor);
        identities.delete(); passphrases.delete();
        byte[] rsa = generate(KeyPair.RSA, 2048, new byte[0]);
        byte[] encrypted = generate(KeyPair.RSA, 2048,
                "correct-passphrase".getBytes(StandardCharsets.UTF_8));
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                try { activity.handleSelectedSshKey(rsa); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue(identities.contains());
            byte[] stored = identities.loadPrivateKey();
            assertArrayEquals(rsa, stored);
            Arrays.fill(stored, (byte) 0);

            byte[] unsupported = "not-a-private-key".getBytes(StandardCharsets.UTF_8);
            scenario.onActivity(activity -> {
                try { activity.handleSelectedSshKey(unsupported); }
                catch (SshKeyImportException expected) { }
            });
            assertArrayEquals(rsa, identities.loadPrivateKey());

            scenario.onActivity(activity -> {
                try { activity.handleSelectedSshKey(encrypted); }
                catch (SshKeyImportException error) { throw new AssertionError(error); }
            });
            onView(withHint(R.string.ssh_key_passphrase_hint)).inRoot(isDialog())
                    .perform(replaceText("wrong-passphrase"));
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());
            assertArrayEquals(rsa, identities.loadPrivateKey());

            identities.delete();
            assertFalse(identities.contains());
            scenario.onActivity(activity -> {
                try { activity.handleSelectedSshKey(rsa); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            Arrays.fill(rsa, (byte) 0);
            assertTrue(new SshIdentityMaterialLoader(identities, passphrases).load()
                    .getPrivateKey().length > 0);
        } finally {
            Arrays.fill(rsa, (byte) 0); Arrays.fill(encrypted, (byte) 0);
            identities.delete(); passphrases.delete();
        }
    }

    private static byte[] generate(int type, int size, byte[] passphrase) throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), type, size);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pair.writeOpenSSHv1PrivateKey(output, passphrase);
        pair.dispose();
        return output.toByteArray();
    }

    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences(
                SharedPreferencesStartupSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private SharedPreferencesStartupSyncSettings repository() {
        return new SharedPreferencesStartupSyncSettings(context);
    }

    private android.content.SharedPreferences scheduledPreferences() {
        return context.getSharedPreferences(
                SharedPreferencesScheduledSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private void grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            shell("pm grant " + context.getPackageName()
                    + " android.permission.POST_NOTIFICATIONS");
            appOpNotifications("allow");
        }
    }

    private void appOpNotifications(String mode) {
        shell("cmd appops set " + context.getPackageName()
                + " POST_NOTIFICATION " + mode);
    }

    private void shell(String command) {
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command)) {
            // Closing the descriptor waits for the tiny shell command to be dispatched.
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
