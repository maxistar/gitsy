package me.maxistar.gitsync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

public class AddProjectActivity extends AppCompatActivity {

    public static final String TAG = "GitSyncDebug";
    EditText repoUrlEditor;

    EditText userNameEditor;

    EditText passwordEditor;
    private Button saveButton;
    private ProjectViewModel viewModel;

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    private static final int REQUEST_CODE_OPEN_TEST_DIRECTORY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_project);

        repoUrlEditor = this.findViewById(R.id.repoUrlEditor);
        userNameEditor = this.findViewById(R.id.userNameEditor);
        passwordEditor = this.findViewById(R.id.passwordEditor);

        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);

        saveButton = findViewById(R.id.buttonSaveProject);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Implement save logic, e.g., save to ViewModel or return result to main activity
                saveProject();
            }
        });

        Button testButton = this.findViewById(R.id.testButton);
        // testButton.setVisibility(View.VISIBLE);
        testButton.setOnClickListener(
                v -> {
                    openFolderPicker(REQUEST_CODE_OPEN_TEST_DIRECTORY);
                }
        );


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
                // storeFolderUri(folderUri);
                // folderUrl = folderUri;

                // new MainActivity.CloneRepoTask(this, gitService, fileStorageService).execute();
                saveAndClose(folderUri.toString());
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
            // isCloning = false;
            // updateUiState();
        }
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


    @Override
    protected void onResume() {
        super.onResume();
    }

    private void saveAndClose(String uri) {
        String rUrl = repoUrlEditor.getText().toString();
        String uName = userNameEditor.getText().toString();
        String pwd = passwordEditor.getText().toString();

        ProjectModel newProject = new ProjectModel(rUrl, uName, pwd, uri);
        viewModel.addProject(getApplicationContext(), newProject);

        Intent startIntent = new Intent(this, ProjectService.class);
        startService(startIntent);

        finish();
    }

    private void saveProject() {
        openFolderPicker(REQUEST_CODE_OPEN_DIRECTORY);
    }
}