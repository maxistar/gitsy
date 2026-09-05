package me.maxistar.gitsy;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.app.TimePickerDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Arrays;

public final class SettingsActivity extends AppCompatActivity {
    private static final int REQUEST_SSH_KEY = 41;
    private static final int MAX_KEY_BYTES = 1024 * 1024;
    private SharedPreferencesStartupSyncSettings repository;
    private RadioGroup modeGroup;
    private RadioGroup intervalGroup;
    private TextView intervalLabel;
    private TextView explanation;
    private boolean restoring;
    private EncryptedFileSshIdentityRepository sshIdentities;
    private EncryptedFileSshPassphraseRepository sshPassphrases;
    private SshIdentityImporter sshImporter;
    private TextView sshSummary;
    private Button sshImport;
    private Button sshDelete;
    private byte[] pendingPrivateKey;
    private static final int REQUEST_NOTIFICATIONS = 42;
    private SharedPreferencesScheduledSyncSettings scheduledRepository;
    private ScheduledSyncScheduler scheduledScheduler;
    private SwitchCompat scheduledEnabled;
    private TextView scheduledTime;
    private TextView scheduledNext;
    private TextView notificationGuidance;
    private boolean restoringScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new SharedPreferencesStartupSyncSettings(getApplicationContext());
        scheduledRepository = new SharedPreferencesScheduledSyncSettings(getApplicationContext());
        scheduledScheduler = new ScheduledSyncScheduler(scheduledRepository,
                new AndroidWorkManagerGateway(getApplicationContext()),
                Clock.systemUTC(), ZoneId.systemDefault());
        modeGroup = findViewById(R.id.startup_sync_mode_group);
        intervalGroup = findViewById(R.id.startup_sync_interval_group);
        intervalLabel = findViewById(R.id.startup_sync_interval_label);
        explanation = findViewById(R.id.startup_sync_mode_explanation);
        sshSummary = findViewById(R.id.settings_ssh_summary);
        sshImport = findViewById(R.id.settings_ssh_import);
        sshDelete = findViewById(R.id.settings_ssh_delete);
        scheduledEnabled = findViewById(R.id.scheduled_sync_enabled);
        scheduledTime = findViewById(R.id.scheduled_sync_time);
        scheduledNext = findViewById(R.id.scheduled_sync_next);
        notificationGuidance = findViewById(R.id.scheduled_sync_notification_guidance);
        try {
            ValueEncryptor encryptor = ServiceLocator.getInstance().getValueEncryptor();
            sshIdentities = new EncryptedFileSshIdentityRepository(getApplicationContext(), encryptor);
            sshPassphrases = new EncryptedFileSshPassphraseRepository(getApplicationContext(), encryptor);
            sshImporter = new SshIdentityImporter(new SshPrivateKeyParser(), sshIdentities, sshPassphrases);
        } catch (Exception error) {
            Toast.makeText(this, R.string.ssh_key_error_storage, Toast.LENGTH_LONG).show();
        }

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

        restoringScheduled = true;
        renderScheduled(scheduledRepository.load());
        restoringScheduled = false;
        scheduledEnabled.setOnCheckedChangeListener((button, enabled) -> {
            if (restoringScheduled) return;
            ScheduledSyncSettings current = scheduledRepository.load();
            ScheduledSyncSettings updated = new ScheduledSyncSettings(
                    enabled, current.getHour(), current.getMinute());
            scheduledRepository.save(updated);
            scheduledScheduler.reconcile();
            renderScheduled(updated);
            if (enabled) requestNotificationPermissionIfNeeded();
        });
        scheduledTime.setOnClickListener(view -> showScheduledTimePicker());
        notificationGuidance.setOnClickListener(view -> openNotificationSettings());

        ((TextView) findViewById(R.id.settings_about_version)).setText(
                getString(R.string.settings_about_version, appVersionName()));
        findViewById(R.id.settings_website_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.WEBSITE));
        findViewById(R.id.settings_documentation_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.DOCUMENTATION));
        findViewById(R.id.settings_terms_row).setOnClickListener(
                view -> openExternalUrl(GitsyPublicLinks.TERMS));
        sshImport.setOnClickListener(view -> openSshKeyDocument());
        sshDelete.setOnClickListener(view -> confirmDeleteSshKey());
        renderSshIdentity();
    }

    @Override protected void onResume() {
        super.onResume();
        if (notificationGuidance != null) renderNotificationGuidance();
    }

    private void showScheduledTimePicker() {
        ScheduledSyncSettings current = scheduledRepository.load();
        new TimePickerDialog(this, (picker, hour, minute) -> updateScheduledTime(hour, minute),
                current.getHour(), current.getMinute(),
                android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    void updateScheduledTime(int hour, int minute) {
        ScheduledSyncSettings current = scheduledRepository.load();
        ScheduledSyncSettings updated = new ScheduledSyncSettings(
                current.isEnabled(), hour, minute);
        scheduledRepository.save(updated);
        scheduledScheduler.reconcile();
        renderScheduled(updated);
    }

    private void renderScheduled(ScheduledSyncSettings settings) {
        restoringScheduled = true;
        scheduledEnabled.setChecked(settings.isEnabled());
        restoringScheduled = false;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, settings.getHour());
        calendar.set(java.util.Calendar.MINUTE, settings.getMinute());
        scheduledTime.setText(getString(R.string.scheduled_sync_time,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.getTime())));
        if (settings.isEnabled()) {
            Instant next = new NextScheduledSyncCalculator(
                    Clock.systemUTC(), ZoneId.systemDefault())
                    .next(settings.getHour(), settings.getMinute());
            scheduledNext.setText(getString(R.string.scheduled_sync_next,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date.from(next))));
            scheduledNext.setVisibility(View.VISIBLE);
        } else {
            scheduledNext.setVisibility(View.GONE);
        }
        renderNotificationGuidance();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) renderNotificationGuidance();
    }

    private void renderNotificationGuidance() {
        boolean denied = scheduledRepository.load().isEnabled()
                && Build.VERSION.SDK_INT >= 33
                && !NotificationManagerCompat.from(this).areNotificationsEnabled();
        notificationGuidance.setVisibility(denied ? View.VISIBLE : View.GONE);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openSshKeyDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, REQUEST_SSH_KEY);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SSH_KEY || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        clearPendingKey();
        try {
            handleSelectedSshKey(readDocument(data.getData()));
        } catch (SshKeyImportException error) {
            if (error.getError() == SshKeyImportError.PASSPHRASE_REQUIRED) showPassphraseDialog();
            else { showKeyError(error.getError()); clearPendingKey(); }
        } catch (Exception error) {
            Toast.makeText(this, R.string.ssh_key_error_read, Toast.LENGTH_LONG).show();
            clearPendingKey();
        }
    }

    void handleSelectedSshKey(byte[] privateKey) throws SshKeyImportException {
        clearPendingKey();
        pendingPrivateKey = privateKey.clone();
        try {
            new SshPrivateKeyParser().parse(pendingPrivateKey, null);
            importPendingKey(null, false);
        } catch (SshKeyImportException error) {
            if (error.getError() == SshKeyImportError.PASSPHRASE_REQUIRED) {
                showPassphraseDialog();
                return;
            }
            throw error;
        }
    }

    private void showPassphraseDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        EditText passphrase = new EditText(this);
        passphrase.setHint(R.string.ssh_key_passphrase_hint);
        passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox retain = new CheckBox(this);
        retain.setText(R.string.ssh_key_retain_passphrase);
        retain.setChecked(true);
        content.addView(passphrase); content.addView(retain);
        new AlertDialog.Builder(this).setTitle(R.string.ssh_key_passphrase_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    byte[] secret = passphrase.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try { importPendingKey(secret, retain.isChecked()); }
                    finally { Arrays.fill(secret, (byte) 0); }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> clearPendingKey())
                .setOnCancelListener(dialog -> clearPendingKey()).show();
    }

    private void importPendingKey(byte[] passphrase, boolean retain) {
        try {
            if (sshImporter == null || pendingPrivateKey == null) throw new IllegalStateException();
            sshImporter.importKey(pendingPrivateKey, passphrase, retain);
            Toast.makeText(this, R.string.ssh_key_import_success, Toast.LENGTH_SHORT).show();
            renderSshIdentity();
        } catch (SshKeyImportException error) {
            showKeyError(error.getError());
        } catch (Exception error) {
            Toast.makeText(this, R.string.ssh_key_error_storage, Toast.LENGTH_LONG).show();
        } finally { clearPendingKey(); }
    }

    private void showKeyError(SshKeyImportError error) {
        int message = error == SshKeyImportError.UNSUPPORTED_ALGORITHM
                ? R.string.ssh_key_error_unsupported
                : error == SshKeyImportError.INCORRECT_PASSPHRASE
                ? R.string.ssh_key_error_passphrase : R.string.ssh_key_error_malformed;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void renderSshIdentity() {
        boolean available = sshIdentities != null && sshIdentities.contains();
        sshDelete.setVisibility(available ? View.VISIBLE : View.GONE);
        sshImport.setText(available ? R.string.ssh_key_replace : R.string.ssh_key_import);
        if (!available) { sshSummary.setText(R.string.ssh_key_not_configured); return; }
        try {
            SshIdentityMetadata metadata = sshIdentities.metadata();
            sshSummary.setText(getString(R.string.ssh_key_summary, metadata.getAlgorithm(),
                    metadata.getKeySize(), metadata.getFingerprint()));
        } catch (Exception error) { sshSummary.setText(R.string.ssh_key_error_storage); }
    }

    private void confirmDeleteSshKey() {
        new AlertDialog.Builder(this).setTitle(R.string.ssh_key_delete_title)
                .setMessage(R.string.ssh_key_delete_message)
                .setPositiveButton(R.string.ssh_key_delete, (dialog, which) -> {
                    try { sshImporter.deleteGlobalIdentity(); renderSshIdentity(); }
                    catch (Exception error) { Toast.makeText(this, R.string.ssh_key_error_storage, Toast.LENGTH_LONG).show(); }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private byte[] readDocument(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new java.io.IOException();
            byte[] buffer = new byte[8192]; int total = 0; int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count; if (total > MAX_KEY_BYTES) throw new java.io.IOException();
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void clearPendingKey() {
        if (pendingPrivateKey != null) Arrays.fill(pendingPrivateKey, (byte) 0);
        pendingPrivateKey = null;
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
