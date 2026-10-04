package me.maxistar.gitsy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends AppCompatActivity implements
        ProjectAdapter.OnProjectListener, StartupSyncPromptDialogFragment.Listener {

    private ProjectViewModel viewModel;
    private RecyclerView recyclerView;
    private ProjectAdapter adapter;
    private View emptyView;
    private StartupSyncSession startupSyncSession;
    private SharedPreferencesStartupSyncSettings startupSettingsRepository;
    private SshOperationAttentionEvent displayedSshAttention;
    private final EventBus.EventListener<SshOperationAttentionEvent> sshAttentionListener =
            event -> runOnUiThread(() -> showSshAttention(event));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.empty_view);
        Button addProjectButton = findViewById(R.id.btn_add_project);
        
        addProjectButton.setOnClickListener(v -> addRepository());
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        if (adapter == null) {
            adapter = new ProjectAdapter(new ArrayList<>(), this);
            recyclerView.setAdapter(adapter);
        }

        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);
        viewModel.getProjects().observe(this, projects -> {
            adapter.setProjects(projects);
            checkIfEmpty(projects.size());
        });
        startupSyncSession = ServiceLocator.getInstance().getStartupSyncSession();
        startupSettingsRepository = new SharedPreferencesStartupSyncSettings(getApplicationContext());
        viewModel.getProjectsLoaded().observe(this, loaded -> {
            if (!Boolean.TRUE.equals(loaded)) return;
            List<ProjectModel> loadedProjects = viewModel.getProjects().getValue();
            handleStartupDecision(startupSyncSession.evaluate(
                    true,
                    ProjectService.started,
                    startupSettingsRepository.load(),
                    loadedProjects == null ? Collections.emptyList() : loadedProjects));
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
                        viewModel.saveProjects(getApplicationContext());
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
        EventBus.getInstance().subscribe(SshOperationAttentionEvent.class, sshAttentionListener);



        // try {
        //     ValueEncryptor ecryptor = new ValueEncryptor();
        //     ecryptor.ensureKey();
        //     String encrypted = ecryptor.encryptValue("sometext", "random");
        //     String decrypted = ecryptor.decryptValue(encrypted, "random");
        //     Log.w("dddfdfdfd", decrypted);
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
    }

    @Override protected void onDestroy() {
        EventBus.getInstance().unsubscribe(SshOperationAttentionEvent.class, sshAttentionListener);
        super.onDestroy();
    }

    private void showSshAttention(SshOperationAttentionEvent event) {
        if (isFinishing() || isDestroyed()) return;
        if (displayedSshAttention == event) return;
        displayedSshAttention = event;
        if (event.getType() == SshOperationAttentionEvent.Type.TRUST_REQUIRED) {
            SshHostTrustRequest request = event.getTrustRequest();
            new AlertDialog.Builder(this).setTitle(R.string.ssh_host_trust_title)
                    .setMessage(getString(R.string.ssh_host_trust_message, request.getHost(),
                            request.getPort(), request.getAlgorithm(), request.getFingerprint()))
                    .setPositiveButton(R.string.ssh_host_trust, (dialog, which) -> {
                        try {
                            if (ServiceLocator.getInstance().acceptSshHost(
                                    getApplicationContext(), request.getToken())) {
                                ServiceLocator.getInstance().clearPendingSshAttention(event);
                                displayedSshAttention = null;
                                event.getProject().setStatus(event.getRetryStatus());
                                viewModel.saveProjects(getApplicationContext());
                                startService(new Intent(this, ProjectService.class));
                            }
                        } catch (Exception error) {
                            android.widget.Toast.makeText(this, R.string.ssh_host_trust_error,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        ServiceLocator.getInstance().declineSshHost(
                                getApplicationContext(), request.getToken());
                        ServiceLocator.getInstance().clearPendingSshAttention(event);
                        displayedSshAttention = null;
                    })
                    .setCancelable(false).show();
            return;
        }
        int title = event.getType() == SshOperationAttentionEvent.Type.HOST_CHANGED
                ? R.string.ssh_host_changed_title : R.string.ssh_key_missing_title;
        int message = event.getType() == SshOperationAttentionEvent.Type.HOST_CHANGED
                ? R.string.ssh_host_changed_message : R.string.ssh_key_missing_message;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.ssh_key_settings_action, (dialog, which) -> {
                    ServiceLocator.getInstance().clearPendingSshAttention(event);
                    displayedSshAttention = null;
                    startActivity(new Intent(this, SettingsActivity.class));
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    ServiceLocator.getInstance().clearPendingSshAttention(event);
                    displayedSshAttention = null;
                }).show();
    }

    private void handleStartupDecision(StartupSyncSession.Decision decision) {
        if (decision.getRememberedMode() != null) {
            StartupSyncSettings current = startupSettingsRepository.load();
            startupSettingsRepository.save(new StartupSyncSettings(
                    decision.getRememberedMode(), current.getInterval()));
        }
        if (decision.getType() == StartupSyncSession.DecisionType.RECOVERY_REQUIRED) {
            showInterruptedOperationRecovery();
        } else if (decision.getType() == StartupSyncSession.DecisionType.PROMPT) {
            if (getSupportFragmentManager().findFragmentByTag(StartupSyncPromptDialogFragment.TAG) == null
                    && !getSupportFragmentManager().isStateSaved()) {
                StartupSyncPromptDialogFragment.newInstance(
                        decision.getPendingCount(), decision.getInterval())
                        .show(getSupportFragmentManager(), StartupSyncPromptDialogFragment.TAG);
            }
        } else if (decision.getType() == StartupSyncSession.DecisionType.START) {
            viewModel.syncProjects(getApplicationContext(), decision.getProjects());
            startService(new Intent(this, ProjectService.class));
        }
    }

    @Override
    public void onStartupSyncResponse(boolean synchronize, boolean remember) {
        List<ProjectModel> current = viewModel.getProjects().getValue();
        handleStartupDecision(startupSyncSession.respond(
                synchronize, remember, current == null ? Collections.emptyList() : current));
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayedSshAttention = null;
        SshOperationAttentionEvent pending = ServiceLocator.getInstance().getPendingSshAttention();
        if (pending != null) showSshAttention(pending);
        viewModel.updateProjects();
        adapter.notifyDataSetChanged();
    }

    private void checkIfEmpty(int size) {
        if (size == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void deleteProjectById(int groupId) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_project_title)
            .setMessage(R.string.delete_project_message)
            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                viewModel.deleteProject(getApplicationContext(), groupId); // groupId used as the position
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (viewModel.workInProgress()) {
            return false;
        }

        if (item.getItemId() == 0) { // Delete option
            deleteProjectById(item.getGroupId());
            return true;
        }
        if (item.getItemId() == 1) { // Synchronyze option
            viewModel.syncProject(getApplicationContext(), item.getGroupId()); // groupId used as the position
            Intent startIntent = new Intent(this, ProjectService.class);
            startService(startIntent);
            return true;
        }
        if (item.getItemId() == 2) { // Edit action
            editProjectById(item.getGroupId());
            return true;
        }
        return false;
    }

    private void editProjectById(int position) {
        editProjectById(position, false);
    }

    private void editProjectById(int position, boolean authenticationRecovery) {
        Intent intent = new Intent(this, EditProjectActivity.class);
        intent.putExtra("position", position);
        intent.putExtra(EditProjectActivity.EXTRA_AUTHENTICATION_RECOVERY, authenticationRecovery);
        startActivity(intent);
    }


    @Override
    public void onProjectDelete(int position) {
        viewModel.deleteProject(getApplicationContext(), position);
    }

    @Override
    public void onProjectCheckCredentials(int position) {
        editProjectById(position, true);
    }


    void addRepository() {
        Intent intent = new Intent(MainActivity.this, EditProjectActivity.class);
        startActivity(intent);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        if (menu instanceof MenuBuilder) {
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // check if the projects are in progress and block unsafe items
        boolean blockMenu = !viewModel.workInProgress();

        MenuItem addRepoMenu = menu.findItem(R.id.menu_add_repo);
        addRepoMenu.setEnabled(blockMenu);

        MenuItem syncAllMenu = menu.findItem(R.id.menu_sync_all);
        syncAllMenu.setEnabled(blockMenu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_add_repo) {
            addRepository();
        } else if (itemId == R.id.menu_sync_all) {
            syncAllRepos();
        } else if (itemId == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void syncAllRepos() {
        viewModel.syncAllProjects(getApplicationContext());
        Intent startIntent = new Intent(this, ProjectService.class);
        startService(startIntent);
    }

    private void showInterruptedOperationRecovery() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.application_was_stopped)
                .setMessage(R.string.application_was_stopped_message)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> resetProjectsStatus())
                .show();
    }

    private void resetProjectsStatus() {
        viewModel.resetProjectsStatus(getApplicationContext());
    }


}
