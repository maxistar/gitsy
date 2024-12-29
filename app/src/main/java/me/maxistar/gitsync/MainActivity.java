package me.maxistar.gitsync;
import org.eclipse.jgit.api.Git;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;


public class MainActivity extends AppCompatActivity {

    public static final String GIT_REMOTE_ADDRESS = "git_remote_address";
    public static final String GIT_REMOTE_USER = "git_remote_user";
    public static final String GIT_REMOTE_PASSWORD = "git_remote_password";
    public static final String TAG = "GitSyncDebug";
    final String FOLDER_NAME = "temp-repo7";
    Uri folderUrl;

    boolean isSynchronizing = false;

    String gitRemoteAddress;

    String gitRemoteUser;

    String gitRemotePassword;

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    EditText repoUrlEditor;

    EditText userNameEditor;

    EditText passwordEditor;

    Button cloneButton;

    Button clearButton;

    Button syncButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        folderUrl = getFolderUri();
        gitRemoteAddress = getStoredValue(GIT_REMOTE_ADDRESS);
        gitRemoteUser = getStoredValue(GIT_REMOTE_USER);
        gitRemotePassword = getStoredValue(GIT_REMOTE_PASSWORD);

        repoUrlEditor = this.findViewById(R.id.repoUrlEditor);
        repoUrlEditor.setText(gitRemoteAddress);
        userNameEditor = this.findViewById(R.id.userNameEditor);
        userNameEditor.setText(gitRemoteUser);
        passwordEditor = this.findViewById(R.id.passwordEditor);
        passwordEditor.setText(gitRemotePassword);

        clearButton = this.findViewById(R.id.clearButton);
        clearButton.setOnClickListener(
                v -> {
                    File tempDir = new File(getCacheDir(), FOLDER_NAME);
                    deleteDirectoryRecursively(tempDir);
                    resetFolderUri();
                    updateUiState();
                }
        );






        cloneButton = this.findViewById(R.id.cloneButton);
        cloneButton.setOnClickListener(
                v -> {
                    storeStringValue(GIT_REMOTE_ADDRESS, String.valueOf(repoUrlEditor.getText()));
                    gitRemoteAddress = String.valueOf(repoUrlEditor.getText());

                    storeStringValue(GIT_REMOTE_USER, String.valueOf(userNameEditor.getText()));
                    gitRemoteUser = String.valueOf(userNameEditor.getText());

                    storeStringValue(GIT_REMOTE_PASSWORD, String.valueOf(passwordEditor.getText()));
                    gitRemotePassword = String.valueOf(passwordEditor.getText());

                    openFolderPicker();
                }
        );

        syncButton = this.findViewById(R.id.syncButton);
        syncButton.setOnClickListener(
                v -> {
                    isSynchronizing = true;
                    updateUiState();
                    new SyncRepoTask().execute();
                }
        );

        updateUiState();

    }

    private void updateUiState() {
        boolean repoNotInitialized = this.folderUrl == null;

        repoUrlEditor.setEnabled(repoNotInitialized);
        userNameEditor.setEnabled(repoNotInitialized);
        passwordEditor.setEnabled(repoNotInitialized);

        cloneButton.setEnabled(repoNotInitialized);
        clearButton.setEnabled(!repoNotInitialized);
        syncButton.setEnabled(!repoNotInitialized && !isSynchronizing);
    }

    public void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            Uri folderUri = data.getData();

            if (folderUri != null) {
                // Persist access permissions
                getContentResolver().takePersistableUriPermission(folderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Save the URI for later use
                storeFolderUri(folderUri);
                this.folderUrl = folderUri;

                new CloneRepoTask(gitRemoteAddress, FOLDER_NAME).execute();



                // Optional: Display or use the URI
                Log.d(TAG, "Selected Folder URI: " + folderUri.toString());
            }
        }
    }

    private void storeFolderUri(Uri folderUri) {
        storeStringValue("folder_uri", folderUri.toString());
    }

    private void storeStringValue(String name, String value) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(name, value);
        editor.apply();
    }

    private void resetFolderUri() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("folder_uri");
        editor.apply();
        this.folderUrl = null;
    }

    private Uri getFolderUri() {
        String value = getStoredValue("folder_uri");
        if (value.isEmpty()) {
            return null;
        }
        return Uri.parse(value);
    }

    private String getStoredValue(String name) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return sharedPreferences.getString(name, "");
    }


    private void moveFilesToSaf(File sourceDir, Uri treeUri) {

        Log.d(TAG, "Move Files to SAF");

        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

        if (pickedDir != null) {
            moveFilesRecursively(sourceDir, pickedDir);
        }

        Log.d(TAG, "Stop Moving Files to SAF");
    }

    private void moveFilesRecursively(File sourceDir, DocumentFile targetDir) {
        if (sourceDir.isDirectory()) {
            // For each file/subdirectory in the source directory
            for (File file : sourceDir.listFiles()) {
                Log.d(TAG, "copy " + file.getName());
                if (file.isDirectory()) {
                    if (file.getName().equals(".git")) {
                        continue;
                    }
                    // Create the subdirectory in SAF target
                    DocumentFile subDir = targetDir.findFile(file.getName());
                    if (subDir == null) {
                        subDir = targetDir.createDirectory(file.getName());
                    }

                    // Recursively move files into this subdirectory
                    moveFilesRecursively(file, subDir);
                } else {
                    // Copy the file into the target directory
                    copyFileToSaf(file, targetDir);
                }
            }
        }
    }

    private void copyFileToSaf(File sourceFile, DocumentFile targetDir) {

        try {
            DocumentFile targetFile = targetDir.findFile(sourceFile.getName());
            if (targetFile != null) {
                //File targetFile = new File(targetDir, file.getName());
                if (targetFile.exists() && isFileUnchangedBasedOnTime(sourceFile, targetFile)) {
                    Log.d(TAG, "Skipping unchanged file: " + sourceFile.getAbsolutePath());
                    return;
                }

                targetFile.delete(); // need to delete otherwise it creates a new file
            }

            // Create a new file in the SAF target directory
            DocumentFile newFile = targetDir.createFile("application/octet-stream", sourceFile.getName());
            if (newFile != null) {
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = getContentResolver().openOutputStream(newFile.getUri())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void copyFromSaf(Uri treeUri, String destinationDirName) {
        Log.d(TAG, "Move Files from SAF");
        File destinationDir = new File(getCacheDir(), destinationDirName);
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

        if (pickedDir != null && pickedDir.isDirectory()) {
            // Ensure the destination directory exists
            if (!destinationDir.exists()) {
                destinationDir.mkdirs();
            }

            // Recursively copy files from SAF to internal storage
            copyFilesRecursivelyFromSaf(pickedDir, destinationDir);
        } else {
            Log.d(TAG, "Invalid SAF directory.");
        }
        Log.d(TAG, "Stop Moving Files from SAF");
    }

    private void copyFilesRecursivelyFromSaf(DocumentFile sourceDir, File targetDir) {
        for (DocumentFile file : sourceDir.listFiles()) {
            if (file.isDirectory()) {
                if (file.getName().equals(".git")) {
                    continue;
                }

                // Create a corresponding subdirectory in the target directory
                File subDir = new File(targetDir, file.getName());
                if (!subDir.exists()) {
                    subDir.mkdirs();
                }

                // Recursively copy files in this subdirectory
                copyFilesRecursivelyFromSaf(file, subDir);
            } else if (file.isFile()) {
                File targetFile = new File(targetDir, file.getName());
                if (targetFile.exists() && isFileUnchangedBasedOnTime(file, targetFile)) {
                    Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                    continue;
                }

                // Copy the file to the target directory
                copyFileFromSaf(file, new File(targetDir, file.getName()));
            }
        }
    }

    private boolean isFileUnchangedBasedOnTime(DocumentFile sourceFile, File targetFile) {
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.lastModified();

        if (sourceLastModified == 0 || targetLastModified == 0) {
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified <= targetLastModified;
    }

    private boolean isFileUnchangedBasedOnTime(File sourceFile,DocumentFile targetFile) {
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.lastModified();

        if (sourceLastModified == 0 || targetLastModified == 0) {
            Log.d(TAG, "source of target time is null");
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified <= targetLastModified;
    }

    private void copyFileFromSaf(DocumentFile sourceFile, File targetFile) {
        try (InputStream in = getContentResolver().openInputStream(sourceFile.getUri());
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            targetFile.setLastModified(sourceFile.lastModified());
            Log.d(TAG, "Copied file: " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listRepositoryFilesByName(String repoDirName) {
        File tempDir = new File(getCacheDir(), repoDirName);
        listRepositoryFiles(tempDir);
    }

    private void listRepositoryFiles(File repoDir) {
        if (repoDir.exists() && repoDir.isDirectory()) {
            File[] files = repoDir.listFiles(); // Get all files and directories
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        Log.d(TAG, "Directory: " + file.getName());
                        // Recursively list files in subdirectory
                        listRepositoryFiles(file);
                    } else {
                        Log.d(TAG, "File: " + file.getName());
                    }
                }
            }
        } else {
            Log.d(TAG, "The repository directory does not exist or is not a directory.");
        }
    }

    private boolean deleteDirectoryRecursively(File directory) {
        if (directory != null && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteDirectoryRecursively(file)) {
                        return false;
                    }
                }
            }
        }
        return directory != null && directory.delete();
    }


    private class CloneRepoTask extends AsyncTask<Void, Void, String> {
        private String repoUrl;
        private String localDirName;

        public CloneRepoTask(String repoUrl, String localDirName) {
            this.repoUrl = repoUrl;
            this.localDirName = localDirName;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                Log.d(TAG, "Start Clone Repo");
                //cloneRepository("https://github.com/maxistar/notes-md.git");

                File tempDir = new File(getCacheDir(), this.localDirName);

                Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(tempDir)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitRemoteUser, gitRemotePassword))
                        .call();

                Log.d(TAG, "Stop Clone Repo");

                moveFilesToSaf(tempDir, folderUrl);

                //listRepositoryFilesByName(this.localDirName);

                return "Repository cloned successfully!";

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {

            updateUiState();
            // Update the UI after cloning
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
        }
    }

    private class SyncRepoTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {

                copyFromSaf(folderUrl, FOLDER_NAME);

                File tempDir = new File(getCacheDir(), FOLDER_NAME);
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

                moveFilesToSaf(tempDir, folderUrl);

                return "Files synchronized successfully!";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Update the UI after cloning
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            isSynchronizing = false;
            updateUiState();
        }
    }

}