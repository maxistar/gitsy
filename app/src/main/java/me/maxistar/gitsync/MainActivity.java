package me.maxistar.gitsync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.io.File;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements ProjectAdapter.OnProjectListener {

    public static final String GIT_REMOTE_ADDRESS = "git_remote_address";
    public static final String GIT_REMOTE_USER = "git_remote_user";
    public static final String GIT_REMOTE_PASSWORD = "git_remote_password";
    public static final String TAG = "GitSyncDebug";
    // static final String FOLDER_NAME = "temp-repo";


    static Uri folderUrl;

    static boolean isSynchronizing = false;

    static boolean isCloning = false;

    static String gitRemoteAddress;

    static String gitRemoteUser;

    static String gitRemotePassword;


    Button cloneButton;

    Button syncButton;

    Button testButton;



    private ProjectViewModel viewModel;
    private RecyclerView recyclerView;
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (adapter == null) {
            adapter = new ProjectAdapter(new ArrayList<>(), this);
            recyclerView.setAdapter(adapter);
        }

        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);
        viewModel.getProjects().observe(this, projects -> {
            adapter.setProjects(projects);
        });
        viewModel.loadProjects(getApplicationContext());
        registerForContextMenu(recyclerView);

        Handler mainHandler = new Handler(Looper.getMainLooper());

        EventBus.getInstance().subscribe(UpdateListEvent.class, new EventBus.EventListener<UpdateListEvent>() {
            @Override
            public void onEvent(UpdateListEvent event) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Received event with message: " + event.getMessage());
                        viewModel.updateProjects();
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });


/*        super.onCreate(savedInstanceState);


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
        //testButton.setVisibility(View.VISIBLE);
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
*/
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.updateProjects();
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 0) { // Delete option
            viewModel.deleteProject(getApplicationContext(), item.getGroupId()); // groupId used as the position
            return true;
        }
        if (item.getItemId() == 1) { // Synchronyze option
            viewModel.syncProject(getApplicationContext(), item.getGroupId()); // groupId used as the position
        }
        return false;
    }

    @Override
    public void onProjectDelete(int position) {
        viewModel.deleteProject(getApplicationContext(), position);
    }


    void syncRepository() {
        isSynchronizing = true;
        updateUiState();
        //new SyncRepoTask(this, gitService, fileStorageService).execute();
    }

    void clearRepository() {
        /*
        File tempDir = new File(getFilesDir(), FOLDER_NAME);
        //fileStorageService.deleteDirectoryRecursivelyAndSaveRegistry(MainActivity.this, tempDir);
        resetFolderUri();
        updateUiState();
         */
    }

    void cloneRepository() {
        /*
        isCloning = true;
        storeStringValue(GIT_REMOTE_ADDRESS, String.valueOf(repoUrlEditor.getText()));
        gitRemoteAddress = String.valueOf(repoUrlEditor.getText());

        storeStringValue(GIT_REMOTE_USER, String.valueOf(userNameEditor.getText()));
        gitRemoteUser = String.valueOf(userNameEditor.getText());

        storeStringValue(GIT_REMOTE_PASSWORD, String.valueOf(passwordEditor.getText()));
        gitRemotePassword = String.valueOf(passwordEditor.getText());
        updateUiState();
        openFolderPicker(REQUEST_CODE_OPEN_DIRECTORY);
        */
    }

    void addRepository() {
        Intent intent = new Intent(MainActivity.this, AddProjectActivity.class);
        startActivity(intent);
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
        } else if (itemId == R.id.menu_add_repo) {
            addRepository();
        } else if (itemId == R.id.menu_reset_repo) {
            clearRepository();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUiState() {
        boolean repoNotInitialized = folderUrl == null;

        /*
        repoUrlEditor.setEnabled(repoNotInitialized && !isCloning);
        userNameEditor.setEnabled(repoNotInitialized && !isCloning);
        passwordEditor.setEnabled(repoNotInitialized && !isCloning);
         */
        cloneButton.setEnabled(repoNotInitialized && !isCloning);
        syncButton.setEnabled(!repoNotInitialized && !isSynchronizing && !isCloning);
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

        Context context;
        GitService gitService;

        FileStorageService fileStorageService;

        public CloneRepoTask(Context context, GitService gitService, FileStorageService fileStorageService) {
            this.context = context.getApplicationContext();
            this.gitService = gitService;
            this.fileStorageService = fileStorageService;
        }

        @Override
        protected String doInBackground(Void... voids) {
            /*
            try {

                gitService.cloneRepository(context, FOLDER_NAME, gitRemoteAddress, gitRemoteUser, gitRemotePassword);

                fileStorageService.copyToSaf(context, FOLDER_NAME, folderUrl);

                return "Repository cloned successfully!";


            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }*/
            return "Error: ";
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            isCloning = false;
            updateUiState();
        }
    }

    private class SyncRepoTask extends AsyncTask<Void, Void, String> {

        Context context;
        GitService gitService;

        FileStorageService fileStorageService;

        public SyncRepoTask(Context context, GitService gitService, FileStorageService fileStorageService) {
            this.context = context.getApplicationContext();
            this.gitService = gitService;
            this.fileStorageService = fileStorageService;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                /*
                fileStorageService.copyFromSaf(context, folderUrl, FOLDER_NAME);

                gitService.syncRepository(context, FOLDER_NAME, gitRemoteUser, gitRemotePassword);

                fileStorageService.copyToSaf(context, FOLDER_NAME, folderUrl);

                */
                return "Files synchronized successfully!";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Update the UI after cloning
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            isSynchronizing = false;
            updateUiState();
        }
    }

}