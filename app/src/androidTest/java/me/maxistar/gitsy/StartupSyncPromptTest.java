package me.maxistar.gitsy;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Matcher;

import java.util.ArrayList;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class StartupSyncPromptTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repository().save(new StartupSyncSettings(
                StartupSyncMode.ASK_IF_STALE, StartupSyncInterval.ONE_HOUR));
        ArrayList<ProjectModel> projects = new ArrayList<>();
        ProjectModel project = new ProjectModel(
                "https://example.invalid/notes.git", "user", "token", "content://notes");
        project.folderName = "prompt-project";
        project.setStatus(ProjectModel.STATUS_READY);
        project.setLastSync(0);
        projects.add(project);
        FileUtils.saveProjectList(context, projects);
        ServiceLocator.getInstance().replaceStartupSyncSessionForTests(new StartupSyncSession());
        ProjectService.started = false;
    }

    @After
    public void tearDown() {
        context.deleteFile("projects.json");
        context.getSharedPreferences(
                SharedPreferencesStartupSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void recreationKeepsOnePromptAndAcceptWithoutRememberKeepsAsk() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onPromptView(withText(R.string.startup_sync_prompt_title)).check(matches(isDisplayed()));
            onPromptView(withId(R.id.startup_sync_remember_choice)).check(matches(not(isChecked())));
            scenario.recreate();
            onPromptView(withText(R.string.startup_sync_prompt_title)).check(matches(isDisplayed()));
            makePendingProjectIneligible(scenario);
            onPromptView(withText(R.string.startup_sync_prompt_synchronize)).perform(click());
            assertEquals(StartupSyncMode.ASK_IF_STALE, repository().load().getMode());
        }
    }

    @Test
    public void rememberedAcceptChangesModeToIfStale() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onPromptView(withText(R.string.startup_sync_prompt_title)).check(matches(isDisplayed()));
            onPromptView(withId(R.id.startup_sync_remember_choice)).perform(click());
            makePendingProjectIneligible(scenario);
            onPromptView(withText(R.string.startup_sync_prompt_synchronize)).perform(click());
            assertEquals(StartupSyncMode.IF_STALE, repository().load().getMode());
            assertEquals(StartupSyncInterval.ONE_HOUR, repository().load().getInterval());
        }
    }

    @Test
    public void rememberedDeclineChangesModeToNever() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onPromptView(withText(R.string.startup_sync_prompt_title)).check(matches(isDisplayed()));
            onPromptView(withId(R.id.startup_sync_remember_choice)).perform(click());
            onPromptView(withText(R.string.startup_sync_prompt_not_now)).perform(click());
            assertEquals(StartupSyncMode.NEVER, repository().load().getMode());
            assertEquals(StartupSyncInterval.ONE_HOUR, repository().load().getInterval());
        }
    }

    @Test
    public void dismissActsAsUnrememberedDecline() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onPromptView(withText(R.string.startup_sync_prompt_title)).check(matches(isDisplayed()));
            pressBack();
            assertEquals(StartupSyncMode.ASK_IF_STALE, repository().load().getMode());
        }
    }

    private void makePendingProjectIneligible(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> ProjectRepository.getInstance().getProjects().get(0)
                .setStatus(ProjectModel.STATUS_SYNC_IN_PROGRESS));
    }

    private SharedPreferencesStartupSyncSettings repository() {
        return new SharedPreferencesStartupSyncSettings(context);
    }

    private ViewInteraction onPromptView(Matcher<View> matcher) {
        return onView(matcher).inRoot(isDialog());
    }
}
