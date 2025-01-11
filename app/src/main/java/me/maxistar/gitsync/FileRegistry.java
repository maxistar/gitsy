package me.maxistar.gitsync;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileRegistry {

    public static final String TAG = "GitSyncDebug";

    FileInfoEntry rootEntry = new FileInfoEntry("", FileInfoEntry.NODE_FOLDER);

    int numberOfFiles = 0;

    public static void saveRegistryToFile(FileRegistry registry, File outputFile) {

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(registry, writer);
            Log.w(TAG, "Registry saved to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to save registry.");
        }
    }

    public static FileRegistry loadRegistryFromFile(File inputFile) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(inputFile)) {
            return gson.fromJson(reader, FileRegistry.class);
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to load registry.");
            return new FileRegistry();
        }
    }
}
