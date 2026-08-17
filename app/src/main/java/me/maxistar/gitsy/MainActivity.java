package me.maxistar.gitsy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends AppCompatActivity implements ProjectAdapter.OnProjectListener {

    private ProjectViewModel viewModel;
    private RecyclerView recyclerView;
    private ProjectAdapter adapter;
    private View emptyView;

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
        StartupSyncCoordinator startupSyncCoordinator = createStartupSyncCoordinator();
        viewModel.getProjectsLoaded().observe(this, loaded -> {
            if (!Boolean.TRUE.equals(loaded)) return;
            List<ProjectModel> loadedProjects = viewModel.getProjects().getValue();
            startupSyncCoordinator.onProjectsChanged(
                    true,
                    ProjectService.started,
                    loadedProjects == null ? Collections.emptyList() : loadedProjects);
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

    private StartupSyncCoordinator createStartupSyncCoordinator() {
        return new StartupSyncCoordinator(
                new SharedPreferencesStartupSyncSettings(getApplicationContext()),
                new StartupSyncPolicy(),
                ServiceLocator.getInstance().getStartupSyncRunGuard(),
                System::currentTimeMillis,
                new StartupSyncCoordinator.Actions() {
                    @Override
                    public void showRecovery() {
                        showInterruptedOperationRecovery();
                    }

                    @Override
                    public void synchronize(List<ProjectModel> projects) {
                        viewModel.syncProjects(getApplicationContext(), projects);
                        startService(new Intent(MainActivity.this, ProjectService.class));
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        Intent intent = new Intent(this, EditProjectActivity.class);
        intent.putExtra("position", position);
        startActivity(intent);
    }


    @Override
    public void onProjectDelete(int position) {
        viewModel.deleteProject(getApplicationContext(), position);
    }


    void addRepository() {
        Intent intent = new Intent(MainActivity.this, EditProjectActivity.class);
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
        appInfo.setText("Android GitSy\nVersion " + versionName);

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
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gitsy.de/terms"));
            startActivity(browserIntent);
        });

        appWebsite.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gitsy.de/"));
            startActivity(browserIntent);
        });

        aboutDialog.show();
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
        if (itemId == R.id.menu_about) {
            showAboutBox();
        } else if (itemId == R.id.menu_add_repo) {
            addRepository();
        } else if (itemId == R.id.menu_sync_all) {
            syncAllRepos();
        } else if (itemId == R.id.menu_startup_sync_settings) {
            showStartupSyncSettings();
        }
        return super.onOptionsItemSelected(item);
    }

    void showStartupSyncSettings() {
        SharedPreferencesStartupSyncSettings repository =
                new SharedPreferencesStartupSyncSettings(getApplicationContext());
        StartupSyncSettings settings = repository.load();
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_startup_sync_settings, null);
        RadioGroup modeGroup = dialogView.findViewById(R.id.startup_sync_mode_group);
        RadioGroup intervalGroup = dialogView.findViewById(R.id.startup_sync_interval_group);
        TextView intervalLabel = dialogView.findViewById(R.id.startup_sync_interval_label);

        modeGroup.check(modeButtonId(settings.getMode()));
        intervalGroup.check(intervalButtonId(settings.getInterval()));
        updateIntervalControls(modeGroup, intervalGroup, intervalLabel);
        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
                updateIntervalControls(group, intervalGroup, intervalLabel));

        new AlertDialog.Builder(this)
                .setTitle(R.string.startup_sync_settings_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> repository.save(
                        new StartupSyncSettings(
                                modeFromButtonId(modeGroup.getCheckedRadioButtonId()),
                                intervalFromButtonId(intervalGroup.getCheckedRadioButtonId()))))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateIntervalControls(
            RadioGroup modeGroup, RadioGroup intervalGroup, TextView intervalLabel) {
        boolean enabled = modeGroup.getCheckedRadioButtonId() == R.id.startup_sync_mode_if_stale;
        intervalGroup.setEnabled(enabled);
        intervalLabel.setEnabled(enabled);
        for (int index = 0; index < intervalGroup.getChildCount(); index++) {
            intervalGroup.getChildAt(index).setEnabled(enabled);
        }
    }

    private int modeButtonId(StartupSyncMode mode) {
        if (mode == StartupSyncMode.NEVER) return R.id.startup_sync_mode_never;
        if (mode == StartupSyncMode.ALWAYS) return R.id.startup_sync_mode_always;
        return R.id.startup_sync_mode_if_stale;
    }

    private StartupSyncMode modeFromButtonId(int id) {
        if (id == R.id.startup_sync_mode_never) return StartupSyncMode.NEVER;
        if (id == R.id.startup_sync_mode_always) return StartupSyncMode.ALWAYS;
        return StartupSyncMode.IF_STALE;
    }

    private int intervalButtonId(StartupSyncInterval interval) {
        if (interval == StartupSyncInterval.FIFTEEN_MINUTES) return R.id.startup_sync_interval_15_minutes;
        if (interval == StartupSyncInterval.SIX_HOURS) return R.id.startup_sync_interval_6_hours;
        if (interval == StartupSyncInterval.TWENTY_FOUR_HOURS) return R.id.startup_sync_interval_24_hours;
        return R.id.startup_sync_interval_1_hour;
    }

    private StartupSyncInterval intervalFromButtonId(int id) {
        if (id == R.id.startup_sync_interval_15_minutes) return StartupSyncInterval.FIFTEEN_MINUTES;
        if (id == R.id.startup_sync_interval_6_hours) return StartupSyncInterval.SIX_HOURS;
        if (id == R.id.startup_sync_interval_24_hours) return StartupSyncInterval.TWENTY_FOUR_HOURS;
        return StartupSyncInterval.ONE_HOUR;
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
