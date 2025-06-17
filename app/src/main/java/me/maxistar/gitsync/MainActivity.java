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
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.io.File;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements ProjectAdapter.OnProjectListener {

    private ProjectViewModel viewModel;
    private RecyclerView recyclerView;
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
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

        new Handler().postDelayed(this::syncAllReposOlderThanHour, 1000);

    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.updateProjects();
        adapter.notifyDataSetChanged();
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
        if (item.getItemId() == 0) { // Delete option
            deleteProjectById(item.getGroupId());
            return true;
        }
        if (item.getItemId() == 1) { // Synchronyze option
            viewModel.syncProject(getApplicationContext(), item.getGroupId()); // groupId used as the position

            Intent startIntent = new Intent(this, ProjectService.class);
            startService(startIntent);
        }
        return false;
    }

    @Override
    public void onProjectDelete(int position) {
        viewModel.deleteProject(getApplicationContext(), position);
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

        if (menu instanceof MenuBuilder) {
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
        if (itemId == R.id.menu_about) {
            showAboutBox();
        } else if (itemId == R.id.menu_add_repo) {
            addRepository();
        } else if (itemId == R.id.menu_sync_all) {
            syncAllRepos();
        }
        return super.onOptionsItemSelected(item);
    }

    private void syncAllRepos() {
        viewModel.syncAllProjects(getApplicationContext());
        Intent startIntent = new Intent(this, ProjectService.class);
        startService(startIntent);
    }

    private void syncAllReposOlderThanHour() {
        if (viewModel.allElementsAreReady()) {
            viewModel.syncAllProjectsOlderThanHour(getApplicationContext());
        }
    }


}