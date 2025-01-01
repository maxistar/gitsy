package me.maxistar.gitsync;

import android.content.Context;
import android.util.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitService {

    public static final String TAG = "GitSyncDebug";

    public void cloneRepository(Context context, String internalFolderName, String repoUrl, String gitRemoteUser, String gitRemotePassword) throws GitAPIException {
        Log.d(TAG, "Start Clone Repo");

        File tempDir = new File(context.getCacheDir(), internalFolderName);

        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempDir)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                .call();

        Log.d(TAG, "Stop Clone Repo");
    }

    public void syncRepository(Context context,String internalFolderName, String gitRemoteUser, String gitRemotePassword) throws IOException, GitAPIException {
        File tempDir = new File(context.getCacheDir(), internalFolderName);
        Git git = Git.open(tempDir);

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

            RevCommit commit = git.commit()
                    .setMessage("commit message")
                    .call();
        }

        git.pull()
                .setRebase(false)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                .call();

        if (hasChanges) {
            git.
                    add()
                    .addFilepattern(".")
                    .call();

            RevCommit commit2 = git.commit()
                    .setMessage("commit, fix conflicts")
                    .call();

            git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                    .call();
        }
    }
}
