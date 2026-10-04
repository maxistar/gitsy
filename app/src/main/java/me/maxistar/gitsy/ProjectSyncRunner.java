package me.maxistar.gitsy;

import android.content.Context;
import android.net.Uri;

final class ProjectSyncRunner {
    enum Phase { IMPORT, GIT, EXPORT }
    interface Progress { void onPhase(Phase phase); }

    static final class Result {
        private final boolean success;
        private final Exception error;
        private Result(boolean success, Exception error) {
            this.success = success; this.error = error;
        }
        static Result success() { return new Result(true, null); }
        static Result failure(Exception error) { return new Result(false, error); }
        boolean isSuccess() { return success; }
        Exception getError() { return error; }
    }

    private final Context context;
    private final FileStorageService storage;
    private final GitService git;
    private final SyncFailureClassifier classifier;

    ProjectSyncRunner(Context context) {
        this(context, new FileStorageService(), new GitService(), new SyncFailureClassifier());
    }

    ProjectSyncRunner(Context context, FileStorageService storage, GitService git,
                      SyncFailureClassifier classifier) {
        this.context = context.getApplicationContext();
        this.storage = storage; this.git = git; this.classifier = classifier;
    }

    Result synchronize(ProjectModel project, Progress progress) {
        SyncCoordinator coordinator = new SyncCoordinator(
                current -> {
                    progress.onPhase(Phase.IMPORT);
                    try {
                        storage.copyFromSaf(context, Uri.parse(current.getFolderUri()),
                                current.getFolderName());
                    } catch (RuntimeException error) {
                        throw new SafAccessException(error);
                    }
                },
                current -> { progress.onPhase(Phase.GIT); git.syncRepository(context, current); },
                current -> {
                    progress.onPhase(Phase.EXPORT);
                    try {
                        storage.copyToSaf(context, current.getFolderName(),
                                Uri.parse(current.getFolderUri()));
                    } catch (RuntimeException error) {
                        throw new SafAccessException(error);
                    }
                },
                System::currentTimeMillis,
                current -> persistAndNotify());
        SyncCoordinator.Result result = coordinator.synchronize(project);
        if (result.isSuccess()) {
            project.setSyncFailureCategory(SyncFailureCategory.NONE);
            persistAndNotify();
            return Result.success();
        }
        project.setSyncFailureCategory(classifier.classify(result.getError(), project));
        persistAndNotify();
        return Result.failure(result.getError());
    }

    private void persistAndNotify() {
        ProjectRepository.getInstance().saveProjects(context);
        EventBus.getInstance().post(new UpdateListEvent("project state changed"));
    }
}
