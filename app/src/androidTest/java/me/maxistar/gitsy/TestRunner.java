package me.maxistar.gitsy;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.runner.AndroidJUnitRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TestRunner extends AndroidJUnitRunner {
    private String previousAutofillService;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
    }

    @Override
    public void onStart() {
        previousAutofillService = shellOutput("settings get secure autofill_service");
        shell("settings put secure autofill_service null");
        shell("cmd autofill reset");
        super.onStart();
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        if (previousAutofillService == null || previousAutofillService.isEmpty()
                || "null".equals(previousAutofillService)) {
            shell("settings delete secure autofill_service");
        } else {
            shell("settings put secure autofill_service " + previousAutofillService);
        }
        shell("cmd autofill reset");
        super.finish(resultCode, results);
    }

    private void shell(String command) {
        shellOutput(command);
    }

    private String shellOutput(String command) {
        try (ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     new ParcelFileDescriptor.AutoCloseInputStream(descriptor),
                     StandardCharsets.UTF_8))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read test Autofill service", error);
        }
    }
}
