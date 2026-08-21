package me.maxistar.gitsy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StartupSyncSessionTest {
    private static final long NOW = 50L * StartupSyncInterval.TWENTY_FOUR_HOURS.getMilliseconds();

    @Test
    public void waitsForLoadThenStartsAutomaticSelectionOnce() {
        StartupSyncSession session = session();
        ProjectModel project = project("one", ProjectModel.STATUS_READY, 0);
        assertType(StartupSyncSession.DecisionType.WAIT,
                session.evaluate(false, false, settings(StartupSyncMode.ALWAYS), Collections.singletonList(project)));
        StartupSyncSession.Decision start = session.evaluate(
                true, false, settings(StartupSyncMode.ALWAYS), Collections.singletonList(project));
        assertType(StartupSyncSession.DecisionType.START, start);
        assertEquals(Collections.singletonList(project), start.getProjects());
        assertFinish(StartupSyncSession.FinishReason.ALREADY_EVALUATED,
                session.evaluate(true, false, settings(StartupSyncMode.ALWAYS), Collections.singletonList(project)));
    }

    @Test
    public void neverAndNoMatchFinishWithTypedReasons() {
        assertFinish(StartupSyncSession.FinishReason.POLICY_DISABLED,
                session().evaluate(true, false, settings(StartupSyncMode.NEVER),
                        Collections.singletonList(project("one", ProjectModel.STATUS_READY, 0))));
        assertFinish(StartupSyncSession.FinishReason.NO_MATCHING_PROJECTS,
                session().evaluate(true, false, settings(StartupSyncMode.IF_STALE),
                        Collections.singletonList(project("one", ProjectModel.STATUS_READY, NOW))));
    }

    @Test
    public void runningServiceFinishesWithoutRecoveryOrStart() {
        assertFinish(StartupSyncSession.FinishReason.SERVICE_RUNNING,
                session().evaluate(true, true, settings(StartupSyncMode.ALWAYS),
                        Collections.singletonList(project("one", ProjectModel.STATUS_SYNC_IN_PROGRESS, 0))));
    }

    @Test
    public void recoveryWinsInEveryModeIncludingAsk() {
        for (StartupSyncMode mode : StartupSyncMode.values()) {
            assertType(StartupSyncSession.DecisionType.RECOVERY_REQUIRED,
                    session().evaluate(true, false, settings(mode),
                            Collections.singletonList(project("one", ProjectModel.STATUS_TO_SYNC, 0))));
        }
    }

    @Test
    public void askRetainsOnePromptAcrossRepeatedDeliveryAndReattachment() {
        StartupSyncSession session = session();
        ProjectModel project = project("one", ProjectModel.STATUS_READY, 0);
        StartupSyncSession.Decision first = session.evaluate(
                true, false, settings(StartupSyncMode.ASK_IF_STALE), Collections.singletonList(project));
        StartupSyncSession.Decision repeated = session.evaluate(
                true, false, settings(StartupSyncMode.ASK_IF_STALE), Collections.singletonList(project));
        assertPrompt(1, StartupSyncInterval.ONE_HOUR, first);
        assertPrompt(1, StartupSyncInterval.ONE_HOUR, repeated);
    }

    @Test
    public void acceptStartsOnlyOriginallySelectedProjects() {
        StartupSyncSession session = session();
        ProjectModel selected = project("selected", ProjectModel.STATUS_READY, 0);
        ProjectModel fresh = project("fresh", ProjectModel.STATUS_READY, NOW);
        session.evaluate(true, false, settings(StartupSyncMode.ASK_IF_STALE), Arrays.asList(selected, fresh));

        ProjectModel addedLater = project("later", ProjectModel.STATUS_READY, 0);
        StartupSyncSession.Decision decision = session.respond(
                true, false, Arrays.asList(selected, fresh, addedLater));
        assertType(StartupSyncSession.DecisionType.START, decision);
        assertEquals(Collections.singletonList(selected), decision.getProjects());
        assertNull(decision.getRememberedMode());
    }

    @Test
    public void acceptSkipsRemovedOrNewlyIneligibleProjects() {
        StartupSyncSession session = session();
        ProjectModel removed = project("removed", ProjectModel.STATUS_READY, 0);
        ProjectModel changed = project("changed", ProjectModel.STATUS_READY, 0);
        session.evaluate(true, false, settings(StartupSyncMode.ASK_IF_STALE), Arrays.asList(removed, changed));
        changed.setStatus(ProjectModel.STATUS_SYNC_IN_PROGRESS);

        assertFinish(StartupSyncSession.FinishReason.NO_MATCHING_PROJECTS,
                session.respond(true, false, Collections.singletonList(changed)));
    }

    @Test
    public void rememberedAcceptReturnsIfStaleAndDuplicateDoesNothing() {
        StartupSyncSession session = session();
        ProjectModel project = project("one", ProjectModel.STATUS_READY, 0);
        session.evaluate(true, false, settings(StartupSyncMode.ASK_IF_STALE), Collections.singletonList(project));
        StartupSyncSession.Decision accepted = session.respond(true, true, Collections.singletonList(project));
        assertType(StartupSyncSession.DecisionType.START, accepted);
        assertEquals(StartupSyncMode.IF_STALE, accepted.getRememberedMode());
        assertFinish(StartupSyncSession.FinishReason.ALREADY_EVALUATED,
                session.respond(true, true, Collections.singletonList(project)));
    }

    @Test
    public void declineAndDismissCanRememberOnlyExplicitChoice() {
        StartupSyncSession remembered = session();
        remembered.evaluate(true, false, settings(StartupSyncMode.ASK_IF_STALE),
                Collections.singletonList(project("one", ProjectModel.STATUS_READY, 0)));
        StartupSyncSession.Decision decline = remembered.respond(false, true, Collections.emptyList());
        assertFinish(StartupSyncSession.FinishReason.USER_DECLINED, decline);
        assertEquals(StartupSyncMode.NEVER, decline.getRememberedMode());

        StartupSyncSession dismissed = session();
        dismissed.evaluate(true, false, settings(StartupSyncMode.ASK_IF_STALE),
                Collections.singletonList(project("two", ProjectModel.STATUS_READY, 0)));
        StartupSyncSession.Decision dismiss = dismissed.respond(false, false, Collections.emptyList());
        assertFinish(StartupSyncSession.FinishReason.USER_DECLINED, dismiss);
        assertNull(dismiss.getRememberedMode());
    }

    private StartupSyncSession session() {
        return new StartupSyncSession(new StartupSyncPolicy(), () -> NOW);
    }

    private StartupSyncSettings settings(StartupSyncMode mode) {
        return new StartupSyncSettings(mode, StartupSyncInterval.ONE_HOUR);
    }

    private ProjectModel project(String id, int status, long lastSync) {
        ProjectModel model = new ProjectModel("https://example.invalid/" + id, "user", "token", "content://" + id);
        model.folderName = id;
        model.setStatus(status);
        model.setLastSync(lastSync);
        return model;
    }

    private void assertType(StartupSyncSession.DecisionType type, StartupSyncSession.Decision decision) {
        assertEquals(type, decision.getType());
    }

    private void assertPrompt(int count, StartupSyncInterval interval, StartupSyncSession.Decision decision) {
        assertType(StartupSyncSession.DecisionType.PROMPT, decision);
        assertEquals(count, decision.getPendingCount());
        assertEquals(interval, decision.getInterval());
    }

    private void assertFinish(StartupSyncSession.FinishReason reason, StartupSyncSession.Decision decision) {
        assertType(StartupSyncSession.DecisionType.FINISH, decision);
        assertEquals(reason, decision.getFinishReason());
    }
}
