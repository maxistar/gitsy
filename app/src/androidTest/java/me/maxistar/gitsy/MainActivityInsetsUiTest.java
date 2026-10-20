package me.maxistar.gitsy;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MainActivityInsetsUiTest {
    private static final int PROJECT_COUNT = 12;
    private static final String REPOSITORY_PREFIX = "https://example.invalid/insets-repository-";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SharedPreferencesStartupSyncSettings(context).save(
                new StartupSyncSettings(StartupSyncMode.NEVER, StartupSyncInterval.ONE_HOUR));
        ProjectService.started = false;

        ArrayList<ProjectModel> projects = new ArrayList<>();
        for (int index = 0; index < PROJECT_COUNT; index++) {
            ProjectModel project = new ProjectModel(
                    REPOSITORY_PREFIX + index, "user", "token", "content://insets/" + index);
            project.folderName = "insets-project-" + index;
            project.setStatus(ProjectModel.STATUS_READY);
            project.setNumberFiles(index + 1);
            project.setLastSync(System.currentTimeMillis());
            projects.add(project);
        }
        FileUtils.saveProjectList(context, projects);
        ServiceLocator.getInstance().replaceStartupSyncSessionForTests(new StartupSyncSession());
    }

    @After
    public void tearDown() {
        context.deleteFile("projects.json");
        context.getSharedPreferences(
                SharedPreferencesStartupSyncSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void firstRepositoryIsBelowTheActionBar() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(REPOSITORY_PREFIX + "0")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                View firstItem = recyclerView.findViewHolderForAdapterPosition(0).itemView;
                View actionBar = activity.findViewById(androidx.appcompat.R.id.action_bar_container);
                Rect firstItemBounds = globalBounds(firstItem);
                Rect actionBarBounds = globalBounds(actionBar);

                assertTrue("first repository overlaps the action bar: first=" + firstItemBounds
                                + ", actionBar=" + actionBarBounds
                                + ", recyclerPaddingTop=" + recyclerView.getPaddingTop(),
                        firstItemBounds.top >= actionBarBounds.bottom);
                if (Build.VERSION.SDK_INT >= 35) {
                    assertTrue("edge-to-edge content must have top inset padding",
                            recyclerView.getPaddingTop() > 0);
                }
            });
        }
    }

    @Test
    public void lastRepositoryIsAboveTheNavigationArea() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(REPOSITORY_PREFIX + "0")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                recyclerView.scrollToPosition(PROJECT_COUNT - 1);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                RecyclerView.ViewHolder holder =
                        recyclerView.findViewHolderForAdapterPosition(PROJECT_COUNT - 1);
                assertNotNull("last repository should be laid out after scrolling", holder);

                Rect itemBounds = globalBounds(holder.itemView);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(
                        activity.getWindow().getDecorView());
                assertNotNull("root window insets should be available", insets);
                int bottomInset = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout()).bottom;
                Rect decorBounds = globalBounds(activity.getWindow().getDecorView());
                assertTrue("last repository overlaps the navigation area",
                        itemBounds.bottom <= decorBounds.bottom - bottomInset);
            });
        }
    }

    @Test
    public void repeatedInsetDispatchDoesNotAccumulatePadding() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(REPOSITORY_PREFIX + "0")).check(matches(isDisplayed()));
            int[] firstPadding = new int[4];
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                firstPadding[0] = recyclerView.getPaddingLeft();
                firstPadding[1] = recyclerView.getPaddingTop();
                firstPadding[2] = recyclerView.getPaddingRight();
                firstPadding[3] = recyclerView.getPaddingBottom();
                ViewCompat.requestApplyInsets(recyclerView);
                ViewCompat.requestApplyInsets(recyclerView);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                assertEquals(firstPadding[0], recyclerView.getPaddingLeft());
                assertEquals(firstPadding[1], recyclerView.getPaddingTop());
                assertEquals(firstPadding[2], recyclerView.getPaddingRight());
                assertEquals(firstPadding[3], recyclerView.getPaddingBottom());
            });
            scenario.recreate();
            onView(withText(REPOSITORY_PREFIX + "0")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                assertEquals(firstPadding[0], recyclerView.getPaddingLeft());
                assertEquals(firstPadding[1], recyclerView.getPaddingTop());
                assertEquals(firstPadding[2], recyclerView.getPaddingRight());
                assertEquals(firstPadding[3], recyclerView.getPaddingBottom());
            });
        }
    }

    private Rect globalBounds(View view) {
        Rect bounds = new Rect();
        assertNotNull("expected view to be present", view);
        assertTrue("expected view to be globally visible", view.getGlobalVisibleRect(bounds));
        return bounds;
    }
}
