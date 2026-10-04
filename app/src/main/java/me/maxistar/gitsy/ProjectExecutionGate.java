package me.maxistar.gitsy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ProjectExecutionGate {
    interface Operation<T> { T run() throws Exception; }

    private final Set<String> active = ConcurrentHashMap.newKeySet();

    boolean tryAcquire(String projectId) { return active.add(projectId); }
    void release(String projectId) { active.remove(projectId); }
    boolean isActive(String projectId) { return active.contains(projectId); }

    <T> T runIfAvailable(String projectId, T unavailable, Operation<T> operation) throws Exception {
        if (!tryAcquire(projectId)) return unavailable;
        try { return operation.run(); }
        finally { release(projectId); }
    }
}
