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

public class FileStorageService {

    public static final String TAG = "GitSyncDebug";


    public void moveFilesToSaf(Context context, String sourceDirName, Uri treeUri) {

        Log.d(TAG, "Move Files to SAF");

        File sourceDir = new File(context.getCacheDir(), sourceDirName);

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, treeUri);

        if (pickedDir != null) {
            moveFilesRecursively(context, sourceDir, pickedDir);
        }

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
            copyFilesRecursivelyFromSaf(context, pickedDir, destinationDir);
        } else {
            Log.d(TAG, "Invalid SAF directory.");
        }
        Log.d(TAG, "Stop Moving Files from SAF");
    }

    private void moveFilesRecursively(Context context, File sourceDir, DocumentFile targetDir) {
        if (sourceDir.isDirectory()) {
            // For each file/subdirectory in the source directory
            for (File file : sourceDir.listFiles()) {
                Log.d(TAG, "copy " + file.getName());
                if (file.isDirectory()) {
                    if (file.getName().equals(".git")) {
                        continue;
                    }
                    // Create the subdirectory in SAF target
                    DocumentFile subDir = targetDir.findFile(file.getName());
                    if (subDir == null) {
                        subDir = targetDir.createDirectory(file.getName());
                    }

                    // Recursively move files into this subdirectory
                    moveFilesRecursively(context, file, subDir);
                } else {
                    // Copy the file into the target directory
                    copyFileToSaf(context, file, targetDir);
                }
            }
        }
    }

    private void copyFileToSaf(Context context, File sourceFile, DocumentFile targetDir) {

        try {
            DocumentFile targetFile = targetDir.findFile(sourceFile.getName());
            if (targetFile != null) {
                //File targetFile = new File(targetDir, file.getName());
                if (targetFile.exists() && isFileUnchangedBasedOnTime(sourceFile, targetFile)) {
                    Log.d(TAG, "Skipping unchanged file: " + sourceFile.getAbsolutePath());
                    return;
                }

                targetFile.delete(); // need to delete otherwise it creates a new file
            }

            // Create a new file in the SAF target directory
            DocumentFile newFile = targetDir.createFile("application/octet-stream", sourceFile.getName());
            if (newFile != null) {
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    private void copyFilesRecursivelyFromSaf(Context context, DocumentFile sourceDir, File targetDir) {
        for (DocumentFile file : sourceDir.listFiles()) {
            if (file.isDirectory()) {
                if (file.getName().equals(".git")) {
                    continue;
                }

                // Create a corresponding subdirectory in the target directory
                File subDir = new File(targetDir, file.getName());
                if (!subDir.exists()) {
                    subDir.mkdirs();
                }

                // Recursively copy files in this subdirectory
                copyFilesRecursivelyFromSaf(context, file, subDir);
            } else if (file.isFile()) {
                File targetFile = new File(targetDir, file.getName());
                if (targetFile.exists() && isFileUnchangedBasedOnTime(file, targetFile)) {
                    Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                    continue;
                }

                // Copy the file to the target directory
                copyFileFromSaf(context, file, new File(targetDir, file.getName()));
            }
        }
    }

    private boolean isFileUnchangedBasedOnTime(DocumentFile sourceFile, File targetFile) {
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.lastModified();

        if (sourceLastModified == 0 || targetLastModified == 0) {
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified <= targetLastModified;
    }

    private boolean isFileUnchangedBasedOnTime(File sourceFile,DocumentFile targetFile) {
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.lastModified();

        if (sourceLastModified == 0 || targetLastModified == 0) {
            Log.d(TAG, "source of target time is null");
            return false; // Treat as changed if timestamps are unavailable
        }

        // If the source file's modification time is greater, the file has changed
        return sourceLastModified <= targetLastModified;
    }

    private void copyFileFromSaf(Context context, DocumentFile sourceFile, File targetFile) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceFile.getUri());
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            targetFile.setLastModified(sourceFile.lastModified());
            Log.d(TAG, "Copied file: " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listRepositoryFilesByName(Context context, String repoDirName) {
        File tempDir = new File(context.getCacheDir(), repoDirName);
        listRepositoryFiles(tempDir);
    }

    private void listRepositoryFiles(File repoDir) {
        if (repoDir.exists() && repoDir.isDirectory()) {
            File[] files = repoDir.listFiles(); // Get all files and directories
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        Log.d(TAG, "Directory: " + file.getName());
                        // Recursively list files in subdirectory
                        listRepositoryFiles(file);
                    } else {
                        Log.d(TAG, "File: " + file.getName());
                    }
                }
            }
        } else {
            Log.d(TAG, "The repository directory does not exist or is not a directory.");
        }
    }

    public boolean deleteDirectoryRecursively(File directory) {
        if (directory != null && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteDirectoryRecursively(file)) {
                        return false;
                    }
                }
            }
        }
        return directory != null && directory.delete();
    }



}
