package me.maxistar.gitsync;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TreeMap;

public class FileStorageService {

    public static final String TAG = "GitSyncDebug";

    private FileRegistry localRegistry;

    private final String registryName;

    public FileStorageService(String registryName) {
        this.registryName = registryName;
    }

    public void loadRegistry(Context context) {
        Log.d(TAG, "load registry");
        File localDir = new File(context.getCacheDir(), registryName);
        localRegistry = FileRegistry.loadRegistryFromFile(localDir);
    }

    public void saveLocalRegistry(Context context) {
        File localDir = new File(context.getCacheDir(), registryName);
        FileRegistry.saveRegistryToFile(localRegistry, localDir);
    }

    public void copyFilesToSaf(Context context, String sourceDirName, Uri treeUri) {

        Log.d(TAG, "Move Files to SAF");

        File sourceDir = new File(context.getCacheDir(), sourceDirName);

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, treeUri);

        moveFilesRecursivelyToSaf(context, sourceDir, pickedDir, localRegistry.getFiles());
        saveLocalRegistry(context);

        Log.d(TAG, "Stop Moving Files to SAF");
    }

    public void copyFromSaf(Context context, Uri treeUri, String destinationDirName) {
        Log.d(TAG, "Move Files from SAF");
        File destinationDir = new File(context.getCacheDir(), destinationDirName);
        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, treeUri);

        if (pickedDir != null && pickedDir.isDirectory()) {
            // Ensure the destination directory exists
            if (!destinationDir.exists()) {
                destinationDir.mkdirs();
            }

            // Recursively copy files from SAF to internal storage
            copyFilesRecursivelyFromSaf(context, pickedDir, destinationDir, localRegistry.getFiles());

            saveLocalRegistry(context);
        } else {
            Log.d(TAG, "Invalid SAF directory.");
        }
        Log.d(TAG, "Stop Moving Files from SAF");
    }

    private void moveFilesRecursivelyToSaf(Context context, File sourceDir, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        //Log.d(TAG, "List Files");
        File[] files = sourceDir.listFiles();
        //Log.d(TAG, "Stop List Files");
        // For each file/subdirectory in the source directory
        for (File file : files) {
            //Log.d(TAG, "copy " + file.getName());
            String filename = file.getName();
            if (file.isDirectory()) {
                if (filename.equals(".git")) {
                    continue;
                }
                // Create the subdirectory in SAF target
                DocumentFile subDir;
                FileRegistry subDirInfo = filesRegistryMap.get(filename);
                if (subDirInfo == null) {
                    subDir = targetDir.createDirectory(filename);
                    subDirInfo = new FileRegistry(filename, FileRegistry.NODE_FOLDER);
                    filesRegistryMap.put(filename, subDirInfo);
                } else {
                    subDir = targetDir.findFile(filename);
                }
                // Recursively move files into this subdirectory
                moveFilesRecursivelyToSaf(context, file, subDir, subDirInfo.getFiles());
            } else {
                // Copy the file into the target directory
                // DocumentFile targetFile = targetDir.findFile(filename);
                FileRegistry fileInfo = filesRegistryMap.get(filename);
                if (fileInfo != null) {
                    //File targetFile = new File(targetDir, file.getName());
                    if (isFileUnchangedBasedOnRegistrySafTime(file, fileInfo)) {
                        //Log.d(TAG, "Skipping unchanged file: " + sourceFile.getAbsolutePath());
                        continue;
                    }
                    DocumentFile targetFile = targetDir.findFile(filename);
                    targetFile.delete(); // need to delete otherwise it creates a new file
                } else {
                    fileInfo = new FileRegistry(filename, FileRegistry.NODE_FILE);
                    filesRegistryMap.put(filename, fileInfo);
                }
                fileInfo.setLocalModificationTime(file.lastModified());
                fileInfo.setSize(file.length());

                copyFileToSaf(context, file, targetDir, fileInfo);
            }
        }
    }

    private void copyFileToSaf(Context context, File sourceFile, DocumentFile targetDir, FileRegistry fileInfo) {
        String filename = fileInfo.getName();
        try {
            // Create a new file in the SAF target directory
            DocumentFile newFile = targetDir.createFile("application/octet-stream", filename);
            if (newFile != null) {
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                fileInfo.setSafModificationTime(newFile.lastModified());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void copyFilesRecursivelyFromSaf(Context context, DocumentFile sourceDir, File targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        //Log.d(TAG, "List Files");
        DocumentFile[] files = sourceDir.listFiles();
        //Log.d(TAG, "Stop List Files");
        for (DocumentFile file : files) {
            String filename = file.getName();
            if (filename.equals(".git")) {
                continue;
            }
            FileRegistry fileInfo = filesRegistryMap.get(filename);
            File targetFile = new File(targetDir, filename);
            if (fileInfo == null) {
                if (file.isDirectory()) {
                    fileInfo = new FileRegistry(filename, FileRegistry.NODE_FOLDER);
                    targetFile.mkdirs();
                } else {
                    fileInfo = new FileRegistry(filename, FileRegistry.NODE_FILE);
                }
                filesRegistryMap.put(filename, fileInfo);

            }
            if (fileInfo.getType() == FileRegistry.NODE_FOLDER) {
                // Create a corresponding subdirectory in the target directory
                // Recursively copy files in this subdirectory
                copyFilesRecursivelyFromSaf(context, file, targetFile, fileInfo.getFiles());
            } else if (file.isFile()) {
                if (isFileUnchangedBasedOnStoredLocalTime(file, fileInfo)) {
                    //Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                    continue;
                }
                // Copy the file to the target directory
                copyFileFromSaf(context, file, targetFile, fileInfo);
            }
        }
    }

    private boolean isFileUnchangedBasedOnStoredLocalTime(DocumentFile sourceFile, FileRegistry targetFile) {
        if (sourceFile.length() != targetFile.getSize()) {
            return false;
        }
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.getLocalModificationTime();

        if (sourceLastModified == 0 || targetLastModified == 0) {
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified != targetLastModified;
    }

    private boolean isFileUnchangedBasedOnRegistrySafTime(File sourceFile, FileRegistry targetFile) {
        if (sourceFile.length() != targetFile.getSize()) {
            return false;
        }
        //Log.d(TAG, "source: " + sourceLastModified + "target: " + targetLastModified);
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.getSafModificationTime();
        if (sourceLastModified == 0 || targetLastModified == 0) {
            // Log.d(TAG, "source of target time is null");
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified != targetLastModified;
    }

    private void copyFileFromSaf(Context context, DocumentFile sourceFile, File targetFile, FileRegistry fileInfo) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceFile.getUri());
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            // targetFile.setLastModified(sourceFile.lastModified());
            // Log.d(TAG, "Copied file: " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileInfo.setSafModificationTime(sourceFile.lastModified());
        fileInfo.setLocalModificationTime(targetFile.lastModified());
        fileInfo.setSize(targetFile.length());
    }

    public boolean deleteDirectoryRecursively(Context context, File directory) {
        if (directory != null && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteDirectoryRecursively(context, file)) {
                        return false;
                    }
                }
            }
        }

        // create a new empty registry and store it
        localRegistry = new FileRegistry("", FileRegistry.NODE_FOLDER);
        saveLocalRegistry(context);

        return directory != null && directory.delete();

    }
}
