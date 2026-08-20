package me.maxistar.gitsy;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public final class SettingsActivity extends AppCompatActivity {
    private SharedPreferencesStartupSyncSettings repository;
    private RadioGroup modeGroup;
    private RadioGroup intervalGroup;
    private TextView intervalLabel;
    private TextView explanation;
    private boolean restoring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new SharedPreferencesStartupSyncSettings(getApplicationContext());
        modeGroup = findViewById(R.id.startup_sync_mode_group);
        intervalGroup = findViewById(R.id.startup_sync_interval_group);
        intervalLabel = findViewById(R.id.startup_sync_interval_label);
        explanation = findViewById(R.id.startup_sync_mode_explanation);

        restoring = true;
        render(repository.load());
        restoring = false;
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (restoring) return;
            StartupSyncSettings current = repository.load();
            StartupSyncMode mode = modeFromButtonId(checkedId);
            repository.save(new StartupSyncSettings(mode, current.getInterval()));
            renderMode(mode);
        });
        intervalGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (restoring) return;
            StartupSyncSettings current = repository.load();
            repository.save(new StartupSyncSettings(
                    current.getMode(), intervalFromButtonId(checkedId)));
        });

        ((TextView) findViewById(R.id.settings_about_version)).setText(
                getString(R.string.settings_about_version, appVersionName()));
        findViewById(R.id.settings_website_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.WEBSITE));
        findViewById(R.id.settings_documentation_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.DOCUMENTATION));
        findViewById(R.id.settings_terms_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.TERMS));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render(StartupSyncSettings settings) {
        modeGroup.check(modeButtonId(settings.getMode()));
        intervalGroup.check(intervalButtonId(settings.getInterval()));
        renderMode(settings.getMode());
    }

    private void renderMode(StartupSyncMode mode) {
        boolean enabled = mode.usesStaleInterval();
        intervalGroup.setEnabled(enabled);
        intervalLabel.setEnabled(enabled);
        for (int index = 0; index < intervalGroup.getChildCount(); index++) {
            intervalGroup.getChildAt(index).setEnabled(enabled);
        }
        explanation.setText(explanationFor(mode));
    }

    private int explanationFor(StartupSyncMode mode) {
        if (mode == StartupSyncMode.NEVER) return R.string.startup_sync_explanation_never;
        if (mode == StartupSyncMode.ASK_IF_STALE) return R.string.startup_sync_explanation_ask;
        if (mode == StartupSyncMode.ALWAYS) return R.string.startup_sync_explanation_always;
        return R.string.startup_sync_explanation_if_stale;
    }

    private int modeButtonId(StartupSyncMode mode) {
        if (mode == StartupSyncMode.NEVER) return R.id.startup_sync_mode_never;
        if (mode == StartupSyncMode.ASK_IF_STALE) return R.id.startup_sync_mode_ask_if_stale;
        if (mode == StartupSyncMode.ALWAYS) return R.id.startup_sync_mode_always;
        return R.id.startup_sync_mode_if_stale;
    }

    private StartupSyncMode modeFromButtonId(int id) {
        if (id == R.id.startup_sync_mode_never) return StartupSyncMode.NEVER;
        if (id == R.id.startup_sync_mode_ask_if_stale) return StartupSyncMode.ASK_IF_STALE;
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

    private String appVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName == null ? getString(R.string.settings_version_unknown) : packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return getString(R.string.settings_version_unknown);
        }
    }

    void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.settings_link_error, Toast.LENGTH_LONG).show();
        }
    }
}
