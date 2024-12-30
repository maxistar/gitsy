package me.maxistar.gitsync;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;


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

    FileStorageService fileStorageService;

    GitService gitService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileStorageService = new FileStorageService();
        gitService = new GitService();

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
                    fileStorageService.deleteDirectoryRecursively(tempDir);
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

                new CloneRepoTask(gitRemoteAddress).execute();



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



    private class CloneRepoTask extends AsyncTask<Void, Void, String> {
        private String repoUrl;

        public CloneRepoTask(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                gitService.cloneRepository(MainActivity.this, FOLDER_NAME, repoUrl, gitRemoteUser, gitRemotePassword);

                fileStorageService.moveFilesToSaf(MainActivity.this, FOLDER_NAME, folderUrl);

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

                fileStorageService.copyFromSaf(MainActivity.this, folderUrl, FOLDER_NAME);

                gitService.syncRepository(MainActivity.this, FOLDER_NAME, gitRemoteUser, gitRemotePassword);

                fileStorageService.moveFilesToSaf(MainActivity.this, FOLDER_NAME, folderUrl);

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