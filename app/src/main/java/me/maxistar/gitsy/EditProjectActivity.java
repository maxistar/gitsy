package me.maxistar.gitsy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

public class EditProjectActivity extends AppCompatActivity {

    public static final String TAG = "GitSyDebug";
    EditText repoUrlEditor;

    EditText userNameEditor;

    EditText passwordEditor;
    EditText sshPortEditor;
    RadioGroup authenticationTypeGroup;
    TextView sshKeyAvailability;
    TextView sshUsernameSource;
    TextView sshPortSource;
    TextView tokenNote;
    TextView httpsNote;
    private Button saveButton;
    private ProjectViewModel viewModel;
    private String fallbackSshUsername = "";
    private String fallbackSshPort = "22";
    private boolean renderingAuthentication;

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    private static final int REQUEST_CODE_OPEN_TEST_DIRECTORY = 2;

    private int editPosition = -1; // -1 means we're creating a new project

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_project);

        repoUrlEditor = this.findViewById(R.id.repoUrlEditor);
        userNameEditor = this.findViewById(R.id.userNameEditor);
        passwordEditor = this.findViewById(R.id.passwordEditor);
        sshPortEditor = findViewById(R.id.sshPortEditor);
        authenticationTypeGroup = findViewById(R.id.authenticationTypeGroup);
        sshKeyAvailability = findViewById(R.id.sshKeyAvailability);
        sshUsernameSource = findViewById(R.id.sshUsernameSource);
        sshPortSource = findViewById(R.id.sshPortSource);
        tokenNote = findViewById(R.id.tokenNote);
        httpsNote = findViewById(R.id.httpsNote);
        saveButton = findViewById(R.id.buttonSaveProject);

        // Set up the Read More link
        TextView readMoreLink = findViewById(R.id.readMoreLink);
        readMoreLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.documentation_url)));
            startActivity(browserIntent);
        });

        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);

        // Check if we're editing an existing project
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("position")) {
            editPosition = intent.getIntExtra("position", -1);
            if (editPosition != -1) {
                // Load the project to edit
                ProjectModel project = viewModel.getProjects().getValue().get(editPosition);
                if (project != null) {
                    repoUrlEditor.setText(project.getRepoUrl());
                    userNameEditor.setText(project.getUserName());
                    passwordEditor.setText(project.getPassword());
                    if (project.getAuthenticationType() == ProjectAuthenticationType.SSH_KEY) {
                        fallbackSshUsername = project.getUserName() == null ? "" : project.getUserName();
                        fallbackSshPort = String.valueOf(project.getSshPort());
                        authenticationTypeGroup.check(R.id.authenticationTypeSsh);
                        sshPortEditor.setText(String.valueOf(project.getSshPort()));
                    }
                    // Update the button text to indicate we're editing
                    saveButton.setText(R.string.update_project);
                    setTitle(R.string.edit_project_title);
                }
            }
        }

        // Add text change listeners to validate input fields
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (renderingAuthentication) return;
                if (isSshMode()) {
                    if (s == userNameEditor.getEditableText() && userNameEditor.isEnabled()) {
                        fallbackSshUsername = s.toString();
                    } else if (s == sshPortEditor.getEditableText() && sshPortEditor.isEnabled()) {
                        fallbackSshPort = s.toString();
                    }
                    renderSshConnection();
                }
                validateFields();
            }
        };

        repoUrlEditor.addTextChangedListener(textWatcher);
        userNameEditor.addTextChangedListener(textWatcher);
        passwordEditor.addTextChangedListener(textWatcher);
        sshPortEditor.addTextChangedListener(textWatcher);
        authenticationTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            renderAuthenticationMode();
            validateFields();
        });

        // Initial validation
        renderAuthenticationMode();
        validateFields();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Implement save logic, e.g., save to ViewModel or return result to main
                // activity
                saveProject();
            }
        });

        Button testButton = this.findViewById(R.id.testButton);
        // testButton.setVisibility(View.VISIBLE);
        testButton.setOnClickListener(
                v -> {
                    openFolderPicker(REQUEST_CODE_OPEN_TEST_DIRECTORY);
                });
    }

    /**
     * Validates input fields and updates the save button state
     */
    private void validateFields() {
        String repoUrl = repoUrlEditor.getText().toString().trim();
        String userName = userNameEditor.getText().toString().trim();
        ProjectModel candidate = new ProjectModel(repoUrl, userName,
                passwordEditor.getText().toString().trim(), "pending");
        if (isSshMode()) {
            SshConnectionResolution resolution = resolveSshConnection();
            if (resolution == null) { saveButton.setEnabled(false); return; }
            candidate.useSshKeyAuthentication(resolution.getUsername(), resolution.getPort());
        }
        boolean valid = new ProjectAuthenticationValidator().validate(candidate)
                == ProjectAuthenticationValidator.Result.VALID;
        if (isSshMode()) valid = valid && hasGlobalSshKey();
        saveButton.setEnabled(valid);
    }

    private void renderAuthenticationMode() {
        boolean ssh = isSshMode();
        passwordEditor.setVisibility(ssh ? View.GONE : View.VISIBLE);
        sshPortEditor.setVisibility(ssh ? View.VISIBLE : View.GONE);
        sshKeyAvailability.setVisibility(ssh ? View.VISIBLE : View.GONE);
        sshUsernameSource.setVisibility(ssh ? View.VISIBLE : View.GONE);
        sshPortSource.setVisibility(ssh ? View.VISIBLE : View.GONE);
        tokenNote.setVisibility(ssh ? View.GONE : View.VISIBLE);
        httpsNote.setVisibility(ssh ? View.GONE : View.VISIBLE);
        sshKeyAvailability.setText(hasGlobalSshKey()
                ? R.string.ssh_key_available : R.string.ssh_key_unavailable);
        if (ssh) renderSshConnection();
        else userNameEditor.setEnabled(true);
    }

    private void renderSshConnection() {
        if (renderingAuthentication) return;
        renderingAuthentication = true;
        try {
            SshConnectionResolution resolution = resolveSshConnection();
            boolean usernameFromUrl = resolution != null && resolution.isUsernameFromUrl();
            boolean portFromUrl = resolution != null && resolution.isPortFromUrl();
            userNameEditor.setEnabled(!usernameFromUrl);
            sshPortEditor.setEnabled(!portFromUrl);
            setTextIfDifferent(userNameEditor, usernameFromUrl
                    ? resolution.getUsername() : fallbackSshUsername);
            setTextIfDifferent(sshPortEditor, portFromUrl
                    ? String.valueOf(resolution.getPort()) : fallbackSshPort);
            sshUsernameSource.setText(usernameFromUrl
                    ? R.string.ssh_value_from_url : R.string.ssh_username_fallback);
            sshPortSource.setText(portFromUrl ? R.string.ssh_value_from_url
                    : "22".equals(fallbackSshPort) ? R.string.ssh_port_default
                    : R.string.ssh_port_fallback);
        } finally { renderingAuthentication = false; }
    }

    private SshConnectionResolution resolveSshConnection() {
        Integer port = null;
        try {
            if (!fallbackSshPort.trim().isEmpty()) port = Integer.parseInt(fallbackSshPort.trim());
            return SshRepositoryUri.resolve(repoUrlEditor.getText().toString().trim(),
                    fallbackSshUsername, port);
        } catch (Exception ignored) { return null; }
    }

    private static void setTextIfDifferent(EditText editor, String value) {
        if (!editor.getText().toString().equals(value)) editor.setText(value);
    }

    private boolean isSshMode() {
        return authenticationTypeGroup.getCheckedRadioButtonId() == R.id.authenticationTypeSsh;
    }

    private int sshPort() {
        try { return Integer.parseInt(sshPortEditor.getText().toString().trim()); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private boolean hasGlobalSshKey() {
        try {
            return new EncryptedFileSshIdentityRepository(getApplicationContext(),
                    ServiceLocator.getInstance().getValueEncryptor()).contains();
        } catch (Exception ignored) { return false; }
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

                // new MainActivity.CloneRepoTask(this, gitService,
                // fileStorageService).execute();
                if (checkIfDirectoryIsEmpty(folderUri)) {
                    showAlertDialog();
                } else {
                    saveAndClose(folderUri.toString());
                }
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

    void saveAndClose(String uri) {
        String rUrl = repoUrlEditor.getText().toString();
        String uName = userNameEditor.getText().toString();
        String pwd = passwordEditor.getText().toString();

        ProjectModel project = new ProjectModel(rUrl, uName, pwd, uri);
        if (isSshMode()) {
            SshConnectionResolution resolution = resolveSshConnection();
            if (resolution == null) return;
            project.useSshKeyAuthentication(resolution.getUsername(), resolution.getPort());
        }
        // Add new project
        viewModel.addProject(getApplicationContext(), project);

        // Start the service for new projects
        Intent startIntent = new Intent(this, ProjectService.class);
        startService(startIntent);


        finish();
    }

    private boolean checkIfDirectoryIsEmpty(Uri uri) {
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
        if (pickedDir != null && pickedDir.exists()) {
            DocumentFile[] files = pickedDir.listFiles();
            return files.length > 0;
        }
        return false;
    }

    private void showAlertDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Non-Empty Folder")
                .setMessage("The folder must be empty in order to proceed.")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveProject() {
        if (editPosition != -1) {
            // Update existing project
            List<ProjectModel> currentProjects = new ArrayList<>(viewModel.getProjects().getValue());
            if (editPosition < currentProjects.size()) {
                String rUrl = repoUrlEditor.getText().toString();
                String uName = userNameEditor.getText().toString();
                String pwd = passwordEditor.getText().toString();
                ProjectModel project = new ProjectModel(rUrl, uName, pwd, currentProjects.get(editPosition).getFolderUri());
                if (isSshMode()) {
                    SshConnectionResolution resolution = resolveSshConnection();
                    if (resolution == null) return;
                    project.useSshKeyAuthentication(resolution.getUsername(), resolution.getPort());
                }
                project.folderName = currentProjects.get(editPosition).getFolderName();
                project.status = currentProjects.get(editPosition).getStatus();
                project.lastSync = currentProjects.get(editPosition).getLastSync();
                project.numberFiles = currentProjects.get(editPosition).getNumberFiles();
                //currentProjects.set(editPosition, project);
                viewModel.setProject(project, editPosition, getApplicationContext());
                viewModel.saveProjects(getApplicationContext());
                viewModel.updateProjects();
            }
            finish();
        } else {
            openFolderPicker(REQUEST_CODE_OPEN_DIRECTORY);
        }
    }

}
