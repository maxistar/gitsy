package me.maxistar.gitsy;

import android.content.Context;
import android.util.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class GitService {
    public static final String TAG = "GitSyDebug";

    public void cloneRepository(Context context, ProjectModel project) throws Exception {
        Log.d(TAG, "Start Clone Repo");
        cloneRepositoryInRoot(context.getFilesDir(), project,
                ServiceLocator.getInstance().getTransportAuthenticationFactory(context));
        Log.d(TAG, "Stop Clone Repo");
    }

    void cloneRepositoryInRoot(File filesRoot, ProjectModel project,
                         ProjectTransportAuthenticationFactory authenticationFactory) throws Exception {
        CloneWorkspace workspace = new CloneWorkspace(filesRoot, project.getFolderName());
        File staging = null;
        try {
            staging = workspace.begin(UUID.randomUUID().toString());
            cloneRepository(staging, project, authenticationFactory);
            workspace.promote(staging);
            staging = null;
        } catch (Exception error) {
            if (staging != null) {
                try { workspace.discard(staging); }
                catch (Exception cleanup) { error.addSuppressed(cleanup); }
            }
            if (project.getAuthenticationType() == ProjectAuthenticationType.HTTPS
                    && !(error instanceof HttpsCloneException)) {
                throw new HttpsCloneException(
                        new HttpsCloneFailureTranslator().translate(error), error);
            }
            throw error;
        }
    }

    public void syncRepository(Context context, ProjectModel project) throws Exception {
        syncRepository(new File(context.getFilesDir(), project.getFolderName()), project,
                ServiceLocator.getInstance().getTransportAuthenticationFactory(context));
    }

    void cloneRepository(File target, ProjectModel project,
                         ProjectTransportAuthenticationFactory authenticationFactory) throws Exception {
        SshConnectionResolution ssh = resolveSsh(project);
        String uri = ssh == null ? project.getRepoUrl() : ssh.getCanonicalUri().toString();
        try (GitTransportAuthentication authentication = ssh == null
                ? authenticationFactory.create(project, operationId(project, "clone"))
                : authenticationFactory.create(project, operationId(project, "clone"), ssh)) {
            try {
                authentication.apply(Git.cloneRepository().setURI(uri).setDirectory(target)).call();
            } catch (Exception error) {
                authentication.rethrowHostFailure();
                throw error;
            }
        }
    }

    void syncRepository(File workingDirectory, ProjectModel project,
                        ProjectTransportAuthenticationFactory authenticationFactory) throws Exception {
        SshConnectionResolution ssh = resolveSsh(project);
        try (GitTransportAuthentication authentication = ssh == null
                ? authenticationFactory.create(project, operationId(project, "sync"))
                : authenticationFactory.create(project, operationId(project, "sync"), ssh);
             Git git = Git.open(workingDirectory)) {
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = hasChanges(status);
            if (hasChanges) {
                git.add().addFilepattern(".").setUpdate(true).call();
                git.commit().setMessage("commit message").call();
            }
            try {
                authentication.apply(git.pull().setRebase(false)).call();
            } catch (Exception error) {
                authentication.rethrowHostFailure();
                throw error;
            }

            status = git.status().call();
            if (hasChanges(status)) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage("commit, fix conflicts").call();
            }
            try {
                authentication.apply(git.push()).call();
            } catch (Exception error) {
                authentication.rethrowHostFailure();
                throw error;
            }
        }
    }

    /** Compatibility helpers retained for local-file HTTPS regression fixtures. */
    void cloneRepository(File target, String url, String user, String password) throws GitAPIException {
        Git.cloneRepository().setURI(url).setDirectory(target)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call();
    }
    void syncRepository(File directory, String user, String password) throws IOException, GitAPIException {
        try (Git git = Git.open(directory)) {
            org.eclipse.jgit.api.Status status = git.status().call();
            if (hasChanges(status)) {
                git.add().addFilepattern(".").setUpdate(true).call();
                git.commit().setMessage("commit message").call();
            }
            git.pull().setRebase(false)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call();
            status = git.status().call();
            if (hasChanges(status)) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage("commit, fix conflicts").call();
            }
            git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call();
        }
    }

    private static boolean hasChanges(org.eclipse.jgit.api.Status status) {
        return !status.getUncommittedChanges().isEmpty() || !status.getUntracked().isEmpty()
                || !status.getModified().isEmpty() || !status.getAdded().isEmpty()
                || !status.getRemoved().isEmpty();
    }
    static SshConnectionResolution resolveSsh(ProjectModel project) throws Exception {
        return project.getAuthenticationType() == ProjectAuthenticationType.SSH_KEY
                ? SshRepositoryUri.resolve(project.getRepoUrl(), project.getUserName(),
                project.getSshPort()) : null;
    }
    private static String operationId(ProjectModel project, String action) {
        return project.getFolderName() + ":" + action + ":" + UUID.randomUUID();
    }
}
