package me.maxistar.gitsy;

import android.content.Context;
import android.util.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitService {

    public static final String TAG = "GitSyDebug";

    public void cloneRepository(Context context, String internalFolderName, String repoUrl, String gitRemoteUser, String gitRemotePassword) throws GitAPIException {
        Log.d(TAG, "Start Clone Repo");
        cloneRepository(new File(context.getFilesDir(), internalFolderName), repoUrl, gitRemoteUser, gitRemotePassword);
        Log.d(TAG, "Stop Clone Repo");
    }

    void cloneRepository(File targetDirectory, String repoUrl, String gitRemoteUser, String gitRemotePassword) throws GitAPIException {
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(targetDirectory)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                .call();
    }

    public void syncRepository(Context context,String internalFolderName, String gitRemoteUser, String gitRemotePassword) throws IOException, GitAPIException {
        syncRepository(new File(context.getFilesDir(), internalFolderName), gitRemoteUser, gitRemotePassword);
    }

    void syncRepository(File workingDirectory, String gitRemoteUser, String gitRemotePassword) throws IOException, GitAPIException {
        try (Git git = Git.open(workingDirectory)) {

            org.eclipse.jgit.api.Status status = git.status().call();

        boolean hasChanges = !status.getUncommittedChanges().isEmpty() ||
                !status.getUntracked().isEmpty() ||
                !status.getModified().isEmpty() ||
                !status.getAdded().isEmpty() ||
                !status.getRemoved().isEmpty();

            if (hasChanges) {
                git.
                    add()
                    .addFilepattern(".")
                    .setUpdate(true)
                    .call();

                git.commit()
                    .setMessage("commit message")
                    .call();
            }

            git.pull()
                .setRebase(false)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                .call();


            status = git.status().call();
            boolean hasChangesAfterPull = !status.getUncommittedChanges().isEmpty() ||
                !status.getUntracked().isEmpty() ||
                !status.getModified().isEmpty() ||
                !status.getAdded().isEmpty() ||
                !status.getRemoved().isEmpty();

            if (hasChangesAfterPull) {
                git.
                    add()
                    .addFilepattern(".")
                    .call();

                git.commit()
                    .setMessage("commit, fix conflicts")
                    .call();

                git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                    .call();
            } else {
                git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                    .call();
            }
        }
    }
}
