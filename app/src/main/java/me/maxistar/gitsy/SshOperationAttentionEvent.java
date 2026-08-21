package me.maxistar.gitsy;

public final class SshOperationAttentionEvent {
    public enum Type { TRUST_REQUIRED, HOST_CHANGED, KEY_MISSING }
    private final Type type;
    private final ProjectModel project;
    private final SshHostTrustRequest trustRequest;
    private final int retryStatus;

    private SshOperationAttentionEvent(Type type, ProjectModel project,
                                       SshHostTrustRequest request, int retryStatus) {
        this.type = type; this.project = project; this.trustRequest = request;
        this.retryStatus = retryStatus;
    }
    public static SshOperationAttentionEvent trust(ProjectModel project,
            SshHostTrustRequest request, int retryStatus) {
        return new SshOperationAttentionEvent(Type.TRUST_REQUIRED, project, request, retryStatus);
    }
    public static SshOperationAttentionEvent changed(ProjectModel project, int retryStatus) {
        return new SshOperationAttentionEvent(Type.HOST_CHANGED, project, null, retryStatus);
    }
    public static SshOperationAttentionEvent missing(ProjectModel project, int retryStatus) {
        return new SshOperationAttentionEvent(Type.KEY_MISSING, project, null, retryStatus);
    }
    public Type getType() { return type; }
    public ProjectModel getProject() { return project; }
    public SshHostTrustRequest getTrustRequest() { return trustRequest; }
    public int getRetryStatus() { return retryStatus; }
}
