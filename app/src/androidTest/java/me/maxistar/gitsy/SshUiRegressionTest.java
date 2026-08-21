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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class SshUiRegressionTest {
    private Context context;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteFile("projects.json");
        new SharedPreferencesStartupSyncSettings(context).save(new StartupSyncSettings(
                StartupSyncMode.NEVER, StartupSyncInterval.ONE_HOUR));
        ServiceLocator.getInstance().clearPendingSshAttention(
                ServiceLocator.getInstance().getPendingSshAttention());
    }

    @After public void tearDown() {
        context.deleteFile("projects.json");
        ServiceLocator.getInstance().clearPendingSshAttention(
                ServiceLocator.getInstance().getPendingSshAttention());
    }

    @Test public void legacyHttpsProjectKeepsHttpsEditorAndCanSwitchWithoutMutation() {
        ArrayList<ProjectModel> projects = new ArrayList<>();
        projects.add(new ProjectModel("https://example.test/notes.git", "user", "token",
                "content://notes"));
        FileUtils.saveProjectList(context, projects);
        ProjectRepository.getInstance().loadProjects(context);

        android.content.Intent intent = new android.content.Intent(context, EditProjectActivity.class);
        intent.putExtra("position", 0);
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.authenticationTypeHttps)).check(matches(isDisplayed()));
            onView(withId(R.id.passwordEditor)).check(matches(isDisplayed()));
            onView(withId(R.id.sshPortEditor)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.authenticationTypeSsh)).perform(click());
            onView(withId(R.id.sshPortEditor)).check(matches(isDisplayed()));
        }
        ProjectModel persisted = FileUtils.loadProjects(context).get(0);
        org.junit.Assert.assertEquals(ProjectAuthenticationType.HTTPS,
                persisted.getAuthenticationType());
        org.junit.Assert.assertEquals("token", persisted.getPassword());
    }

    @Test public void createsHttpsProjectThroughDocumentResultAndReopensIt() throws Exception {
        try (ActivityScenario<EditProjectActivity> scenario = ActivityScenario.launch(
                new Intent(context, EditProjectActivity.class))) {
            onView(withId(R.id.repoUrlEditor)).perform(replaceText("https://example.invalid/notes.git"));
            onView(withId(R.id.userNameEditor)).perform(replaceText("user"));
            onView(withId(R.id.passwordEditor)).perform(replaceText("token"));
            scenario.onActivity(activity -> activity.saveAndClose("content://notes/https-create"));
        }
        ProjectModel created = FileUtils.loadProjects(context).get(0);
        org.junit.Assert.assertEquals(ProjectAuthenticationType.HTTPS, created.getAuthenticationType());
        org.junit.Assert.assertEquals("token", created.getPassword());
    }

    @Test public void savesAndReopensSshProjectWithGlobalKey() throws Exception {
        EncryptedFileSshIdentityRepository identities = new EncryptedFileSshIdentityRepository(
                context, ServiceLocator.getInstance().getValueEncryptor());
        identities.save(new SshIdentityMetadata("RSA", 2048, "SHA256:ui-test", false),
                "test-private-material".getBytes(StandardCharsets.UTF_8));
        ProjectModel ssh = new ProjectModel("git@example.test:notes.git", "git", "", "content://notes");
        ssh.useSshKeyAuthentication("git", 2222);
        ArrayList<ProjectModel> projects = new ArrayList<>(); projects.add(ssh);
        FileUtils.saveProjectList(context, projects);
        ProjectRepository.getInstance().loadProjects(context);
        Intent edit = new Intent(context, EditProjectActivity.class).putExtra("position", 0);
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(edit)) {
            onView(withId(R.id.authenticationTypeSsh)).check(matches(isDisplayed()));
            onView(withId(R.id.sshPortEditor)).perform(replaceText("2200"));
            onView(withId(R.id.buttonSaveProject)).perform(click());
        }
        ProjectRepository.getInstance().loadProjects(context);
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(edit)) {
            onView(withId(R.id.sshPortEditor)).check(matches(withText("2200")));
        } finally { identities.delete(); }
    }

    @Test public void sshEditorTransitionsBetweenUrlDerivedAndFallbackValues() throws Exception {
        EncryptedFileSshIdentityRepository identities = configuredIdentity();
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(
                new Intent(context, EditProjectActivity.class))) {
            onView(withId(R.id.authenticationTypeSsh)).perform(click());
            onView(withId(R.id.repoUrlEditor)).perform(replaceText(
                    "ssh://git@example.test:1022/group/repo.git"));
            onView(withId(R.id.userNameEditor)).check(matches(withText("git")))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.sshPortEditor)).check(matches(withText("1022")))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.sshUsernameSource)).check(matches(withText(R.string.ssh_value_from_url)));

            onView(withId(R.id.repoUrlEditor)).perform(replaceText("git@example.test:group/repo.git"));
            onView(withId(R.id.userNameEditor)).check(matches(withText("git")))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.sshPortEditor)).check(matches(withText("22")))
                    .check(matches(isEnabled()));

            onView(withId(R.id.repoUrlEditor)).perform(replaceText("ssh://example.test/group/repo.git"));
            onView(withId(R.id.userNameEditor)).check(matches(isEnabled()));
            onView(withId(R.id.sshPortEditor)).check(matches(isEnabled()));
        } finally { identities.delete(); }
    }

    @Test public void mismatchedStoredFallbacksRenderAndPersistUrlValues() throws Exception {
        EncryptedFileSshIdentityRepository identities = configuredIdentity();
        ProjectModel ssh = new ProjectModel(
                "ssh://git@example.test:1022/group/repo.git", "stale-user", "", "content://notes");
        ssh.useSshKeyAuthentication("stale-user", 2200);
        ArrayList<ProjectModel> projects = new ArrayList<>(); projects.add(ssh);
        FileUtils.saveProjectList(context, projects);
        ProjectRepository.getInstance().loadProjects(context);
        Intent edit = new Intent(context, EditProjectActivity.class).putExtra("position", 0);
        try (ActivityScenario<EditProjectActivity> ignored = ActivityScenario.launch(edit)) {
            onView(withId(R.id.repoUrlEditor)).check(matches(withText(ssh.getRepoUrl())));
            onView(withId(R.id.userNameEditor)).check(matches(withText("git")))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.sshPortEditor)).check(matches(withText("1022")))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.buttonSaveProject)).perform(click());
        }
        ProjectModel saved = FileUtils.loadProjects(context).get(0);
        org.junit.Assert.assertEquals("git", saved.getUserName());
        org.junit.Assert.assertEquals(1022, saved.getSshPort());
        org.junit.Assert.assertEquals(ssh.getRepoUrl(), saved.getRepoUrl());
        identities.delete();
    }

    @Test public void unknownHostDialogSurvivesRecreationAndCanBeDeclinedOnce() {
        ProjectModel project = new ProjectModel("git@example.test:notes.git", "git", "", "x");
        project.useSshKeyAuthentication("git", 2222);
        SshHostIdentity identity = new SshHostIdentity("example.test", 2222, "ssh-ed25519",
                "host-key".getBytes(StandardCharsets.UTF_8));
        SshHostTrustRequest request = new SshHostTrustRequest("test-token", identity);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.trust(project, request, ProjectModel.STATUS_TO_SYNC));
            onView(withText(R.string.ssh_host_trust_title)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(containsString(request.getFingerprint()))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText(R.string.ssh_host_trust_title)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
        }
        org.junit.Assert.assertNull(ServiceLocator.getInstance().getPendingSshAttention());
    }

    @Test public void changedHostAndMissingKeyAreActionableDialogs() {
        ProjectModel project = new ProjectModel("git@example.test:notes.git", "git", "", "x");
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.changed(project, ProjectModel.STATUS_TO_SYNC));
            onView(withText(R.string.ssh_host_changed_title)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.ssh_key_settings_action)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());

            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.missing(project, ProjectModel.STATUS_TO_SYNC));
            onView(withText(R.string.ssh_key_missing_title)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
        }
    }

    @Test public void acceptingUnknownHostPersistsTrust() throws Exception {
        MemoryTrustedHosts trusted = new MemoryTrustedHosts();
        SshHostTrustCoordinator coordinator = new SshHostTrustCoordinator(trusted);
        ServiceLocator.getInstance().replaceSshHostTrustCoordinatorForTests(coordinator);
        SshHostIdentity identity = new SshHostIdentity("accept.example", 2222, "ssh-ed25519",
                "accept-key".getBytes(StandardCharsets.UTF_8));
        SshHostTrustRequest request = null;
        try { coordinator.verify("accept-operation", identity); }
        catch (SshHostTrustRequiredException required) { request = required.getRequest(); }
        assertNotNull(request);
        ProjectModel project = new ProjectModel("git@accept.example:notes.git", "git", "", "x");

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.trust(project, request, ProjectModel.STATUS_READY));
            onView(withText(R.string.ssh_host_trust)).inRoot(isDialog()).perform(click());
        }
        org.junit.Assert.assertEquals(TrustedHostMatch.MATCH, trusted.check(identity));
    }

    @Test public void sshStringsExistInEverySupportedLocaleAndNeverContainSecrets() {
        int[] ids = { R.string.authentication_ssh_key, R.string.ssh_port,
                R.string.ssh_value_from_url, R.string.ssh_username_fallback,
                R.string.ssh_port_fallback, R.string.ssh_port_default,
                R.string.ssh_key_import, R.string.ssh_host_trust_title,
                R.string.ssh_host_changed_title, R.string.ssh_key_missing_title };
        for (Locale locale : new Locale[] { Locale.ENGLISH, Locale.GERMAN, Locale.ITALIAN,
                new Locale("ru") }) {
            android.content.res.Configuration configuration =
                    new android.content.res.Configuration(context.getResources().getConfiguration());
            configuration.setLocale(locale);
            Context localized = context.createConfigurationContext(configuration);
            for (int id : ids) {
                String value = localized.getString(id);
                assertFalse(value.trim().isEmpty());
                assertFalse(value.contains("PRIVATE KEY"));
                assertFalse(value.contains("passphrase-secret"));
            }
        }
    }

    private EncryptedFileSshIdentityRepository configuredIdentity() throws Exception {
        EncryptedFileSshIdentityRepository identities = new EncryptedFileSshIdentityRepository(
                context, ServiceLocator.getInstance().getValueEncryptor());
        identities.save(new SshIdentityMetadata("RSA", 2048, "SHA256:ui-test", false),
                "test-private-material".getBytes(StandardCharsets.UTF_8));
        return identities;
    }

    private static final class MemoryTrustedHosts implements TrustedHostRepository {
        private final Map<String, SshHostIdentity> values = new HashMap<>();
        @Override public TrustedHostMatch check(SshHostIdentity identity) {
            SshHostIdentity old = values.get(identity.getHost() + ":" + identity.getPort());
            return old == null ? TrustedHostMatch.UNKNOWN
                    : old.sameKey(identity) ? TrustedHostMatch.MATCH : TrustedHostMatch.CHANGED;
        }
        @Override public void trustUnknown(SshHostIdentity identity) {
            values.put(identity.getHost() + ":" + identity.getPort(), identity);
        }
        @Override public void replace(SshHostIdentity identity) { trustUnknown(identity); }
        @Override public void remove(String host, int port) { values.remove(host + ":" + port); }
    }
}
