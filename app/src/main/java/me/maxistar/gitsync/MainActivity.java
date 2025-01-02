package me.maxistar.gitsync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;


public class MainActivity extends AppCompatActivity {

    public static final String GIT_REMOTE_ADDRESS = "git_remote_address";
    public static final String GIT_REMOTE_USER = "git_remote_user";
    public static final String GIT_REMOTE_PASSWORD = "git_remote_password";
    public static final String TAG = "GitSyncDebug";
    final String FOLDER_NAME = "temp-repo";

    static final String REGISTRY_JSON = "registry.json";

    static Uri folderUrl;

    static boolean isSynchronizing = false;

    static boolean isCloning = false;

    static String gitRemoteAddress;

    static String gitRemoteUser;

    static String gitRemotePassword;

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    private static final int REQUEST_CODE_OPEN_TEST_DIRECTORY = 2;

    EditText repoUrlEditor;

    EditText userNameEditor;

    EditText passwordEditor;

    Button cloneButton;

    Button syncButton;

    Button testButton;

    FileStorageService fileStorageService;

    GitService gitService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileStorageService = new FileStorageService(REGISTRY_JSON);
        fileStorageService.loadRegistry(this);

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

        cloneButton = this.findViewById(R.id.cloneButton);
        cloneButton.setOnClickListener(
                v -> {
                    cloneRepository();
                }
        );

        testButton = this.findViewById(R.id.testButton);
        // testButton.setVisibility(View.VISIBLE);
        testButton.setOnClickListener(
                v -> {
                    openFolderPicker(REQUEST_CODE_OPEN_TEST_DIRECTORY);
                }
        );

        syncButton = this.findViewById(R.id.syncButton);
        syncButton.setOnClickListener(
                v -> {
                    syncRepository();
                }
        );

        updateUiState();

    }

    void syncRepository() {
        isSynchronizing = true;
        updateUiState();
        new SyncRepoTask().execute();
    }

    void clearRepository() {
        File tempDir = new File(getCacheDir(), FOLDER_NAME);
        fileStorageService.deleteDirectoryRecursivelyAndSaveRegistry(MainActivity.this, tempDir);
        resetFolderUri();
        updateUiState();
    }

    void cloneRepository() {
        isCloning = true;
        storeStringValue(GIT_REMOTE_ADDRESS, String.valueOf(repoUrlEditor.getText()));
        gitRemoteAddress = String.valueOf(repoUrlEditor.getText());

        storeStringValue(GIT_REMOTE_USER, String.valueOf(userNameEditor.getText()));
        gitRemoteUser = String.valueOf(userNameEditor.getText());

        storeStringValue(GIT_REMOTE_PASSWORD, String.valueOf(passwordEditor.getText()));
        gitRemotePassword = String.valueOf(passwordEditor.getText());
        updateUiState();
        openFolderPicker(REQUEST_CODE_OPEN_DIRECTORY);
    }

    void showAboutBox() {
        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about_box, null);

        String versionName = "1.0.0"; // Default value in case version retrieval fails
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Set app info text with the version
        TextView appInfo = dialogView.findViewById(R.id.app_info);
        appInfo.setText("Android GitSync\nVersion " + versionName);

        // Create and show the dialog
        AlertDialog aboutDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("About")
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .create();

        // Set click listeners for links
        TextView termsAndConditions = dialogView.findViewById(R.id.terms_and_conditions);
        TextView appWebsite = dialogView.findViewById(R.id.app_website);

        termsAndConditions.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms"));
            startActivity(browserIntent);
        });

        appWebsite.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            startActivity(browserIntent);
        });

        aboutDialog.show();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        if(menu instanceof MenuBuilder){
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_clone_repo) {
            cloneRepository();
        } else if (itemId == R.id.menu_sync) {
            syncRepository();
        } else if (itemId == R.id.menu_about) {
            showAboutBox();
        } else if (itemId == R.id.menu_reset_repo) {
            clearRepository();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUiState() {
        boolean repoNotInitialized = folderUrl == null;

        repoUrlEditor.setEnabled(repoNotInitialized && !isCloning);
        userNameEditor.setEnabled(repoNotInitialized && !isCloning);
        passwordEditor.setEnabled(repoNotInitialized && !isCloning);

        cloneButton.setEnabled(repoNotInitialized && !isCloning);
        syncButton.setEnabled(!repoNotInitialized && !isSynchronizing && !isCloning);
    }

    public void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            Uri folderUri = data.getData();
            Log.i(TAG, "selected folder: " + folderUri.toString());

            if (folderUri != null) {
                // Persist access permissions
                getContentResolver().takePersistableUriPermission(folderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Save the URI for later use
                storeFolderUri(folderUri);
                folderUrl = folderUri;

                new CloneRepoTask(gitRemoteAddress).execute();


                // Optional: Display or use the URI
                Log.d(TAG, "Selected Folder URI: " + folderUri.toString());
            }
        } else if (requestCode == REQUEST_CODE_OPEN_TEST_DIRECTORY && resultCode == RESULT_OK) {
            Uri folderUri = data.getData();
            if (folderUri != null) {
                // Persist access permissions
                getContentResolver().takePersistableUriPermission(folderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            persistUriPermissions(data);
            Log.d(TAG, "Selected Folder URI: " + folderUri.toString());
        } else if (resultCode == RESULT_CANCELED) {
            isCloning = false;
            updateUiState();
        }
    }

    private void storeFolderUri(Uri folderUri) {
        storeStringValue("folder_uri", folderUri.toString());
    }

    @SuppressLint("WrongConstant")
    private void persistUriPermissions(Intent data) {
        // Check for the freshest data.
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
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
        folderUrl = null;
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

                fileStorageService.copyToSaf(MainActivity.this, FOLDER_NAME, folderUrl);

                return "Repository cloned successfully!";

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            isCloning = false;
            updateUiState();
        }
    }

    private class SyncRepoTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {

                fileStorageService.copyFromSaf(MainActivity.this, folderUrl, FOLDER_NAME);

                gitService.syncRepository(MainActivity.this, FOLDER_NAME, gitRemoteUser, gitRemotePassword);

                fileStorageService.copyToSaf(MainActivity.this, FOLDER_NAME, folderUrl);

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