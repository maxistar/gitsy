package me.maxistar.gitsy;

public class ProjectModel {

    static final int STATUS_TO_CLONE = 0;

    static final int STATUS_CLONING = 1;

    static final int STATUS_READY = 2;

    static final int STATUS_TO_SYNC = 3;

    static final int STATUS_SYNC_IN_PROGRESS = 4;

    static final int STATUS_CLONING_ERROR = 5;

    static final int STATUS_SYNC_ERROR = 6;

    String folderName;

    String repoUrl;

    String userName;

    String password;

    ProjectAuthenticationType authenticationType = ProjectAuthenticationType.HTTPS;

    int sshPort = 22;

    String folderUri;

    long lastSync;

    int numberFiles;

    int status = STATUS_TO_CLONE;

    public long getLastSync() {
        return lastSync;
    }

    public void setLastSync(long lastSync) {
        this.lastSync = lastSync;
    }

    public ProjectModel(String repoUrl, String userName, String password, String folderUri) {
        this.folderName = generateFolderName();
        this.repoUrl = repoUrl;
        this.userName = userName;
        this.password = password;
        this.folderUri = folderUri;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public ProjectAuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void useHttpsAuthentication(String userName, String password) {
        this.authenticationType = ProjectAuthenticationType.HTTPS;
        this.userName = userName;
        this.password = password;
    }

    public void useSshKeyAuthentication(String userName) {
        useSshKeyAuthentication(userName, 22);
    }

    public void useSshKeyAuthentication(String userName, int sshPort) {
        this.authenticationType = ProjectAuthenticationType.SSH_KEY;
        this.userName = userName;
        this.password = null;
        this.sshPort = sshPort;
    }

    public String getFolderUri() {
        return folderUri;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getNumberFiles() {
        return numberFiles;
    }

    public void setNumberFiles(int numberFiles) {
        this.numberFiles = numberFiles;
    }

    private String generateFolderName() {
        // Get the current timestamp in milliseconds
        long currentTimeMillis = System.currentTimeMillis();

        // Construct the folder name
        String folderName = "project_" + currentTimeMillis;

        return folderName;
    }

}
