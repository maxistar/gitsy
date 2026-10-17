package me.maxistar.gitsy;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HttpsCloneRecoveryUiTest {
    private static final String ORIGINAL_TOKEN = "original-secret-token";
    private Context context;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteFile("projects.json");
        new SharedPreferencesStartupSyncSettings(context).save(new StartupSyncSettings(
                StartupSyncMode.NEVER, StartupSyncInterval.ONE_HOUR));
        saveFailedProject();
    }

    @After public void tearDown() {
        context.stopService(new Intent(context, ProjectService.class));
        context.deleteFile("projects.json");
    }

    @Test public void opensRecoveryFromProjectAndCancelPreservesCredentialsAndFailure() {
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(containsString(ORIGINAL_TOKEN))).check(doesNotExist());
            onView(withId(R.id.item_check_credentials)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.credentialRecoveryGuidance)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonSaveProject)).check(matches(withText(R.string.save_and_retry)));
            onView(withText(containsString("TransportException"))).check(doesNotExist());
            androidx.test.espresso.Espresso.pressBack();
        }
        ProjectModel persisted = FileUtils.loadProjects(context).get(0);
        assertEquals(ORIGINAL_TOKEN, persisted.getPassword());
        assertEquals(CloneFailureCategory.AUTHENTICATION,
                persisted.getCloneFailureCategory());
        assertEquals(ProjectModel.STATUS_CLONING_ERROR, persisted.getStatus());
    }

    @Test public void saveCorrectedCredentialsClearsOldFailureAndStartsCloneRetry() throws Exception {
        Intent recovery = new Intent(context, EditProjectActivity.class)
                .putExtra("position", 0)
                .putExtra(EditProjectActivity.EXTRA_AUTHENTICATION_RECOVERY, true);
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(recovery)) {
            onView(withId(R.id.passwordEditor)).perform(replaceText("corrected-token"));
            onView(withId(R.id.buttonSaveProject)).perform(click());
        }

        ProjectModel persisted = waitForCorrectedProject();
        assertEquals("corrected-token", persisted.getPassword());
        assertNotEquals(CloneFailureCategory.AUTHENTICATION,
                persisted.getCloneFailureCategory());
        assertTrue(persisted.getStatus() == ProjectModel.STATUS_TO_CLONE
                || persisted.getStatus() == ProjectModel.STATUS_CLONING
                || persisted.getStatus() == ProjectModel.STATUS_CLONING_ERROR);
    }

    @Test public void recoveryStringsExistInEverySupportedLocaleAndContainNoSecret() {
        int[] ids = { R.string.https_clone_authentication_error, R.string.check_credentials,
                R.string.credential_recovery_guidance, R.string.save_and_retry };
        for (Locale locale : new Locale[] { Locale.ENGLISH, Locale.GERMAN, Locale.ITALIAN,
                new Locale("ru") }) {
            android.content.res.Configuration configuration =
                    new android.content.res.Configuration(context.getResources().getConfiguration());
            configuration.setLocale(locale);
            Context localized = context.createConfigurationContext(configuration);
            for (int id : ids) {
                String value = localized.getString(id);
                assertFalse(value.trim().isEmpty());
                assertFalse(value.contains(ORIGINAL_TOKEN));
            }
        }
    }

    private void saveFailedProject() {
        ProjectModel project = new ProjectModel("https://127.0.0.1:1/notes.git", "notes-user",
                ORIGINAL_TOKEN, "content://notes");
        project.folderName = "https-recovery-ui";
        project.setStatus(ProjectModel.STATUS_CLONING_ERROR);
        project.setCloneFailureCategory(CloneFailureCategory.AUTHENTICATION);
        ArrayList<ProjectModel> projects = new ArrayList<>();
        projects.add(project);
        FileUtils.saveProjectList(context, projects);
        ProjectRepository.getInstance().loadProjects(context);
    }

    private ProjectModel waitForCorrectedProject() throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            ProjectModel project = FileUtils.loadProjects(context).get(0);
            if ("corrected-token".equals(project.getPassword())) return project;
            Thread.sleep(50L);
        }
        fail("Corrected credentials were not persisted");
        return null;
    }
}
