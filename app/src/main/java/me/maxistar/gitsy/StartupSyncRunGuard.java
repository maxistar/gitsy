package me.maxistar.gitsy;

import java.util.concurrent.atomic.AtomicBoolean;

public final class StartupSyncRunGuard {
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    public boolean claim() {
        return claimed.compareAndSet(false, true);
    }
}
