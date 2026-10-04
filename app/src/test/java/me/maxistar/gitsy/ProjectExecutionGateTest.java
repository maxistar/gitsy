package me.maxistar.gitsy;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProjectExecutionGateTest {
    @Test public void duplicateProjectIsBlockedButDifferentProjectIsIndependent() {
        ProjectExecutionGate gate = new ProjectExecutionGate();
        assertTrue(gate.tryAcquire("one"));
        assertFalse(gate.tryAcquire("one"));
        assertTrue(gate.tryAcquire("two"));
    }

    @Test public void finallyReleaseAllowsLaterExecution() throws Exception {
        ProjectExecutionGate gate = new ProjectExecutionGate();
        try {
            gate.runIfAvailable("one", false, () -> { throw new Exception("failure"); });
            fail("Expected failure");
        } catch (Exception expected) {
            assertEquals("failure", expected.getMessage());
        }
        assertTrue(gate.tryAcquire("one"));
    }

    @Test public void concurrentCallForSameProjectCannotEnter() throws Exception {
        ProjectExecutionGate gate = new ProjectExecutionGate();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            assertTrue(gate.tryAcquire("shared"));
            acquired.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { gate.release("shared"); }
        });
        first.start();
        assertTrue(acquired.await(5, TimeUnit.SECONDS));
        assertFalse(gate.tryAcquire("shared"));
        release.countDown(); first.join(5000);
        assertTrue(gate.tryAcquire("shared"));
    }
}
