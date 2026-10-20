package me.maxistar.gitsy;

public final class ManualSyncOperationGate {
    private boolean pending;

    public boolean begin() {
        if (pending) return false;
        pending = true;
        return true;
    }

    public boolean complete() {
        if (!pending) return false;
        pending = false;
        return true;
    }

    public boolean isPending() {
        return pending;
    }

    public void restore(boolean pending) {
        this.pending = pending;
    }
}
