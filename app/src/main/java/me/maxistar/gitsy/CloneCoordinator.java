package me.maxistar.gitsy;

final class CloneCoordinator {
    interface Operation { void run(ProjectModel project) throws Exception; }
    interface ExportOperation { int run(ProjectModel project) throws Exception; }
    interface Clock { long now(); }
    interface StateListener { void changed(ProjectModel project); }

    static final class Result {
        private final Exception error;
        Result(Exception error) { this.error = error; }
        boolean isSuccess() { return error == null; }
        Exception getError() { return error; }
    }

    private final Operation clone;
    private final ExportOperation export;
    private final Clock clock;
    private final StateListener listener;

    CloneCoordinator(Operation clone, ExportOperation export, Clock clock, StateListener listener) {
        this.clone = clone;
        this.export = export;
        this.clock = clock;
        this.listener = listener;
    }

    Result clone(ProjectModel project) {
        try {
            project.setStatus(ProjectModel.STATUS_CLONING);
            listener.changed(project);
            clone.run(project);
            project.setCloneFailureCategory(CloneFailureCategory.NONE);
            listener.changed(project);
            int totalFiles = export.run(project);
            project.setStatus(ProjectModel.STATUS_READY);
            project.setNumberFiles(totalFiles);
            project.setLastSync(clock.now());
            listener.changed(project);
            return new Result(null);
        } catch (Exception error) {
            project.setStatus(ProjectModel.STATUS_CLONING_ERROR);
            if (error instanceof HttpsCloneException) {
                project.setCloneFailureCategory(((HttpsCloneException) error).getCategory());
            }
            listener.changed(project);
            return new Result(error);
        }
    }
}
