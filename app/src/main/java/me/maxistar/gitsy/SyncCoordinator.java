package me.maxistar.gitsy;

final class SyncCoordinator {
    interface SafImporter {
        void copyFromSaf(ProjectModel project) throws Exception;
    }

    interface GitSynchronizer {
        void synchronize(ProjectModel project) throws Exception;
    }

    interface SafExporter {
        void copyToSaf(ProjectModel project) throws Exception;
    }

    interface Clock {
        long currentTimeMillis();
    }

    interface StateReporter {
        void stateChanged(ProjectModel project);
    }

    static final class Result {
        private final Exception error;

        private Result(Exception error) {
            this.error = error;
        }

        static Result success() {
            return new Result(null);
        }

        static Result failure(Exception error) {
            return new Result(error);
        }

        boolean isSuccess() {
            return error == null;
        }

        Exception getError() {
            return error;
        }
    }

    private final SafImporter importer;
    private final GitSynchronizer gitSynchronizer;
    private final SafExporter exporter;
    private final Clock clock;
    private final StateReporter stateReporter;

    SyncCoordinator(
            SafImporter importer,
            GitSynchronizer gitSynchronizer,
            SafExporter exporter,
            Clock clock,
            StateReporter stateReporter) {
        this.importer = importer;
        this.gitSynchronizer = gitSynchronizer;
        this.exporter = exporter;
        this.clock = clock;
        this.stateReporter = stateReporter;
    }

    Result synchronize(ProjectModel project) {
        project.setStatus(ProjectModel.STATUS_SYNC_IN_PROGRESS);
        stateReporter.stateChanged(project);
        try {
            importer.copyFromSaf(project);
            gitSynchronizer.synchronize(project);
            exporter.copyToSaf(project);
            project.setStatus(ProjectModel.STATUS_READY);
            project.setLastSync(clock.currentTimeMillis());
            stateReporter.stateChanged(project);
            return Result.success();
        } catch (Exception error) {
            project.setStatus(ProjectModel.STATUS_SYNC_ERROR);
            stateReporter.stateChanged(project);
            return Result.failure(error);
        }
    }
}
