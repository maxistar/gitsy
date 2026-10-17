package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManualSyncOperationGateTest {
    @Test public void operationCompletesOnlyOnce() {
        ManualSyncOperationGate gate = new ManualSyncOperationGate();

        assertTrue(gate.begin());
        assertFalse(gate.begin());
        assertTrue(gate.complete());
        assertFalse(gate.complete());
    }

    @Test public void restoredPendingOperationCanCompleteOnce() {
        ManualSyncOperationGate gate = new ManualSyncOperationGate();
        gate.restore(true);

        assertTrue(gate.isPending());
        assertTrue(gate.complete());
        assertFalse(gate.isPending());
        assertFalse(gate.complete());
    }
}
