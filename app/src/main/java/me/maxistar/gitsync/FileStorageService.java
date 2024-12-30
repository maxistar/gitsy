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
    public static final String REGISTRY_JSON = "registry.json";

    private FileRegistry localRegistry;

    public void loadRegistry(Context context) {
        Log.d(TAG, "load registry");

        /* try {
            File file = new File(context.getCacheDir(), REGISTRY_JSON);
            StringBuilder content = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            Log.w(TAG, content.toString().trim());
        } catch (Exception e) {
        } */

        File localDir = new File(context.getCacheDir(), REGISTRY_JSON);
        localRegistry = FileRegistry.loadRegistryFromFile(localDir);
    }

    public void saveLocalRegistry(Context context) {
        File localDir = new File(context.getCacheDir(), REGISTRY_JSON);
        FileRegistry.saveRegistryToFile(localRegistry, localDir);
    }

    public void copyFilesToSaf(Context context, String sourceDirName, Uri treeUri) {

        Log.d(TAG, "Move Files to SAF");

        File sourceDir = new File(context.getCacheDir(), sourceDirName);

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, treeUri);

        if (pickedDir != null) {
            moveFilesRecursively(context, sourceDir, pickedDir, localRegistry.getFiles());
            saveLocalRegistry(context);
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
            copyFilesRecursivelyFromSaf(context, pickedDir, destinationDir, localRegistry.getFiles());

            saveLocalRegistry(context);
        } else {
            Log.d(TAG, "Invalid SAF directory.");
        }
        Log.d(TAG, "Stop Moving Files from SAF");
    }

    private void moveFilesRecursively(Context context, File sourceDir, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        if (sourceDir.isDirectory()) {
            File[] files = sourceDir.listFiles();
            // Arrays.sort(files, (file1, file2) -> {
            //    if (file1.getName() != null && file2.getName() != null) {
            //        return file1.getName().compareToIgnoreCase(file2.getName());
            //    }
            //    return 0; // Handle null names gracefully
            //});
            // For each file/subdirectory in the source directory
            for (File file : files) {
                //Log.d(TAG, "copy " + file.getName());
                if (file.isDirectory()) {
                    String filename = file.getName();
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
                    moveFilesRecursively(context, file, subDir, subDirInfo.getFiles());
                } else {
                    // Copy the file into the target directory
                    copyFileToSaf(context, file, targetDir, filesRegistryMap);
                }
            }
        }
    }

    private void copyFileToSaf(Context context, File sourceFile, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {

        try {
            // DocumentFile targetFile = targetDir.findFile(sourceFile.getName());
            FileRegistry fileInfo = filesRegistryMap.get(sourceFile.getName());
            if (fileInfo != null) {
                //File targetFile = new File(targetDir, file.getName());
                if (isFileUnchangedBasedOnRegistrySafTime(sourceFile, fileInfo)) {
                    //Log.d(TAG, "Skipping unchanged file: " + sourceFile.getAbsolutePath());
                    return;
                }

                DocumentFile targetFile = targetDir.findFile(sourceFile.getName());
                targetFile.delete(); // need to delete otherwise it creates a new file
            } else {
                fileInfo = new FileRegistry(sourceFile.getName(), FileRegistry.NODE_FILE);
                filesRegistryMap.put(sourceFile.getName(), fileInfo);
            }
            fileInfo.setLocalModificationTime(sourceFile.lastModified());
            fileInfo.setSize(sourceFile.length());

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
                fileInfo.setSafModificationTime(newFile.lastModified());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void copyFilesRecursivelyFromSaf(Context context, DocumentFile sourceDir, File targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        for (DocumentFile file : sourceDir.listFiles()) {
            String filename = file.getName();
            FileRegistry fileInfo = filesRegistryMap.get(filename);
            if (file.isDirectory()) {
                if (filename.equals(".git")) {
                    continue;
                }
                if (fileInfo == null) {
                    fileInfo = new FileRegistry(filename, FileRegistry.NODE_FOLDER);
                    filesRegistryMap.put(filename, fileInfo);
                }
                // Create a corresponding subdirectory in the target directory
                File subDir = new File(targetDir, filename);
                if (!subDir.exists()) {
                    subDir.mkdirs();
                }

                // Recursively copy files in this subdirectory
                copyFilesRecursivelyFromSaf(context, file, subDir, fileInfo.getFiles());
            } else if (file.isFile()) {
                if (fileInfo == null) {
                    fileInfo = new FileRegistry(filename, FileRegistry.NODE_FILE);
                    filesRegistryMap.put(filename, fileInfo);
                }

                File targetFile = new File(targetDir, filename);
                if (targetFile.exists() && isFileUnchangedBasedOnStoredLocalTime(file, fileInfo)) {
                    //Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                    continue;
                }

                // Copy the file to the target directory
                copyFileFromSaf(context, file, targetFile, fileInfo);
            }
        }
    }

    private boolean isFileUnchangedBasedOnStoredLocalTime(DocumentFile sourceFile, FileRegistry targetFile) {
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
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.lastModified();
        long targetLastModified = targetFile.getSafModificationTime();

        //Log.d(TAG, "source: " + sourceLastModified + "target: " + targetLastModified);

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
