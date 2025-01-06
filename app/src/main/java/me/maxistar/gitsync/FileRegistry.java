package me.maxistar.gitsync;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

public class FileRegistry {

    public static final int NODE_FILE = 0;

    public static final int NODE_FOLDER = 1;

    private String name; // Name of the file or folder
    private String safUri;

    private int type; // "file" or "folder"
    private long size; // Size of the file (bytes)
    private long localModificationTime; // Last modification time of the local file
    private long safModificationTime; // Last modification time of the SAF file
    private TreeMap<String, FileRegistry> files; // Subtree for folders

    public static final String TAG = "GitSyncDebug";

    // Constructor
    public FileRegistry(String name, int type) {
        this.name = name;
        this.type = type;
        this.size = 0;
        this.localModificationTime = 0;
        this.safModificationTime = 0;
        if (type == NODE_FOLDER) {
            this.files = new TreeMap<String, FileRegistry>(
                    String::compareTo
            );
        } else {
            this.files = null;
        }
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSafUri() {
        return safUri;
    }

    public void setSafUri(String safUri) {
        this.safUri = safUri;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLocalModificationTime() {
        return localModificationTime;
    }

    public void setLocalModificationTime(long localModificationTime) {
        this.localModificationTime = localModificationTime;
    }

    public long getSafModificationTime() {
        return safModificationTime;
    }

    public void setSafModificationTime(long safModificationTime) {
        this.safModificationTime = safModificationTime;
    }

    public TreeMap<String, FileRegistry> getFiles() {
        return files;
    }

    public void setFiles(TreeMap<String, FileRegistry> files) {
        this.files = files;
    }

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
            return new FileRegistry("", NODE_FOLDER);
        }
    }
}