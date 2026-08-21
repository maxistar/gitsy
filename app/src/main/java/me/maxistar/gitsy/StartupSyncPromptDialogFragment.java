package me.maxistar.gitsy;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

public final class StartupSyncPromptDialogFragment extends AppCompatDialogFragment {
    public interface Listener {
        void onStartupSyncResponse(boolean synchronize, boolean remember);
    }

    public static final String TAG = "startup-sync-prompt";
    private static final String ARG_COUNT = "count";
    private static final String ARG_INTERVAL = "interval";
    private Listener listener;
    private boolean responseDelivered;

    public static StartupSyncPromptDialogFragment newInstance(
            int projectCount, StartupSyncInterval interval) {
        StartupSyncPromptDialogFragment fragment = new StartupSyncPromptDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_COUNT, projectCount);
        arguments.putString(ARG_INTERVAL, interval.getPreferenceValue());
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof Listener)) {
            throw new IllegalStateException("Host Activity must implement Listener");
        }
        listener = (Listener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_startup_sync_prompt, null);
        CheckBox remember = content.findViewById(R.id.startup_sync_remember_choice);
        int count = requireArguments().getInt(ARG_COUNT);
        StartupSyncInterval interval = StartupSyncInterval.fromPreferenceValue(
                requireArguments().getString(ARG_INTERVAL));
        String intervalText = getString(intervalLabel(interval));

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.startup_sync_prompt_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.startup_sync_prompt_message, count, count, intervalText))
                .setView(content)
                .setPositiveButton(R.string.startup_sync_prompt_synchronize,
                        (dialog, which) -> deliver(true, remember.isChecked()))
                .setNegativeButton(R.string.startup_sync_prompt_not_now,
                        (dialog, which) -> deliver(false, remember.isChecked()))
                .create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        deliver(false, false);
        super.onCancel(dialog);
    }

    @Override
    public void onDetach() {
        listener = null;
        super.onDetach();
    }

    private void deliver(boolean synchronize, boolean remember) {
        if (responseDelivered) return;
        responseDelivered = true;
        if (listener != null) listener.onStartupSyncResponse(synchronize, remember);
    }

    private int intervalLabel(StartupSyncInterval interval) {
        if (interval == StartupSyncInterval.FIFTEEN_MINUTES) return R.string.startup_sync_interval_15_minutes;
        if (interval == StartupSyncInterval.SIX_HOURS) return R.string.startup_sync_interval_6_hours;
        if (interval == StartupSyncInterval.TWENTY_FOUR_HOURS) return R.string.startup_sync_interval_24_hours;
        return R.string.startup_sync_interval_1_hour;
    }
}
