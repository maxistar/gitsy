package me.maxistar.gitsy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StartupSyncPolicy {
    public List<ProjectModel> select(
            StartupSyncSettings settings, long currentTimeMillis, List<ProjectModel> projects) {
        if (settings.getMode() == StartupSyncMode.NEVER || projects.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProjectModel> selected = new ArrayList<>();
        for (ProjectModel project : projects) {
            if (!isEligible(project)) continue;
            if (settings.getMode() == StartupSyncMode.ALWAYS
                    || isStale(project, currentTimeMillis, settings.getInterval().getMilliseconds())) {
                selected.add(project);
            }
        }
        return selected;
    }

    private boolean isEligible(ProjectModel project) {
        int status = project.getStatus();
        return status == ProjectModel.STATUS_READY
                || status == ProjectModel.STATUS_CLONING_ERROR
                || status == ProjectModel.STATUS_SYNC_ERROR;
    }

    private boolean isStale(ProjectModel project, long now, long interval) {
        long lastSync = project.getLastSync();
        if (lastSync > now) return false;
        return now - lastSync >= interval;
    }
}
