package me.maxistar.gitsync;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;

public class FileStorageService {

    private static FileStorageService instance;

    public static final String TAG = "GitSyncDebug";
    public static final int BUFFER_SIZE = 1024;

    private FileRegistry localRegistry;

    public FileStorageService() {
    }

    public void copyToSaf(Context context, String sourceDirName, Uri treeUri) {
        String registryFilename = sourceDirName + ".json";
        loadRegistry(context, registryFilename);
        Log.d(TAG, "Move Files to SAF");

        File sourceDir = new File(context.getFilesDir(), sourceDirName);

        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                documentId
        );

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, childrenUri);

        transferChangesRecursivelyToSaf(context, sourceDir, pickedDir, localRegistry.getFiles());
        saveLocalRegistry(context, registryFilename);

        Log.d(TAG, "Stop Moving Files to SAF");
    }

    public void copyFromSaf(Context context, Uri treeUri, String destinationDirName) {
        String registryFilename = destinationDirName + ".json";
        loadRegistry(context, registryFilename);
        Log.d(TAG, "Move Files from SAF");
        File destinationDir = new File(context.getFilesDir(), destinationDirName);

        // Query parameters
        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                documentId
        );

        // Recursively copy files from SAF to internal storage
        transferChangesRecursivelyFromSaf(context, childrenUri, destinationDir, localRegistry.getFiles());
        saveLocalRegistry(context, registryFilename);

        Log.d(TAG, "Stop Moving Files from SAF");
    }

    public void deleteLocalDirectoryRecursively(Context context, String directoryName) {
        File directory = new File(context.getFilesDir(), directoryName);
        deleteDirectoryRecursively(directory);

        String registryFilename = directoryName + ".json";
        File registryFile = new File(context.getFilesDir(), registryFilename);
        registryFile.delete();
    }

    private void loadRegistry(Context context, String registryFilename) {
        Log.d(TAG, "load registry");
        File localDir = new File(context.getFilesDir(), registryFilename);
        localRegistry = FileRegistry.loadRegistryFromFile(localDir);
    }

    private void saveLocalRegistry(Context context, String registryFilename) {
        File localDir = new File(context.getFilesDir(), registryFilename);
        FileRegistry.saveRegistryToFile(localRegistry, localDir);
    }

    private void transferChangesRecursivelyToSaf(Context context, File sourceDir, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {

        File[] files = sourceDir.listFiles();

        Arrays.sort(files, Comparator.comparing(File::getName));

        FileRegistry[] registry = filesRegistryMap.values().toArray(new FileRegistry[0]);

        int i = 0;
        int j = 0;

        while (i < registry.length || j < files.length) {
            if (i == registry.length) {
                // add a new file
                handleAddingToSaf(context, files[j], targetDir, filesRegistryMap);
                j++;
                continue;
            }
            if (j == files.length) {
                // remove file
                handleRemovingToSaf(registry[i].getName(), targetDir, filesRegistryMap);
                i++;
                continue;
            }
            if (!registry[i].getName().equals(files[j].getName())) {
                // to decide later add of remove
                if (registry[i].getName().compareTo(files[j].getName()) > 0) {
                    handleAddingToSaf(context, files[j], targetDir, filesRegistryMap);
                    j++;
                } else {
                    handleRemovingToSaf(registry[i].getName(), targetDir, filesRegistryMap);
                    i++;
                }

                continue;
            }
            // apply changes fo current file of directory if any
            handleChangesToSaf(context, files[j], targetDir, registry[i]);
            j++;
            i++;
        }
    }

    private void handleChangesToSaf(Context context, File file, DocumentFile targetDir, FileRegistry fileInfo) {
        String filename = fileInfo.getName();
        if (".git".equals(filename)) {
            return;
        }
        if (fileInfo.getType() == FileRegistry.NODE_FOLDER) {
            // Create a corresponding subdirectory in the target directory
            // Recursively copy files in this subdirectory
            DocumentFile targetFile = targetDir.findFile(filename);
            transferChangesRecursivelyToSaf(context, file, targetFile, fileInfo.getFiles());
        } else if (file.isFile()) {
            if (isFileUnchangedBasedOnRegistrySafTime(file, fileInfo)) {
                // Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                // no changed needed
                return;
            }
            // Copy the file to the target directory
            DocumentFile targetFile = targetDir.findFile(filename);
            targetFile.delete(); // need to delete otherwise it creates a new file
            copyFileToSaf(context, file, targetDir, fileInfo);
        }
    }

    private void handleRemovingToSaf(String filename, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        DocumentFile targetFile = targetDir.findFile(filename);
        // File targetFile = new File(targetDir, filename);
        if (targetFile.isDirectory()) {
            deleteDirectoryRecursively(targetFile);
        } else {
            targetFile.delete();
        }
        filesRegistryMap.remove(filename);
    }

    private void handleAddingToSaf(Context context, File sourceFile, DocumentFile targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        String filename = sourceFile.getName();
        if (".git".equals(filename)) {
            return;
        }
        // File targetFile = new File(targetDir, filename);
        FileRegistry fileInfo;
        if (sourceFile.isDirectory()) {
            fileInfo = new FileRegistry(filename, FileRegistry.NODE_FOLDER);
            // should we put it inside?
            DocumentFile subDir = targetDir.createDirectory(filename);
            transferChangesRecursivelyToSaf(context, sourceFile, subDir, fileInfo.getFiles());
        } else {
            fileInfo = new FileRegistry(filename, FileRegistry.NODE_FILE);
            fileInfo.setLocalModificationTime(sourceFile.lastModified());
            fileInfo.setSize(sourceFile.length());
            copyFileToSaf(context, sourceFile, targetDir, fileInfo);
        }
        filesRegistryMap.put(filename, fileInfo);
    }

    private void copyFileToSaf(Context context, File sourceFile, DocumentFile targetDir, FileRegistry fileInfo) {
        String filename = fileInfo.getName();
        try {
            // Create a new file in the SAF target directory
            DocumentFile newFile = targetDir.createFile("application/octet-stream", filename);
            if (newFile != null) {
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                    byte[] buffer = new byte[BUFFER_SIZE];
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

    private void transferChangesRecursivelyFromSaf(Context context, Uri sourceDir, File targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {

        SAFFileInfo[] files = FileUtils.listFilesInFolder(context, sourceDir).toArray(new SAFFileInfo[0]);

        Arrays.sort(files, Comparator.comparing(SAFFileInfo::getFileName));

        FileRegistry[] registry = filesRegistryMap.values().toArray(new FileRegistry[0]);

        // Arrays.sort(registry, Comparator.comparing(FileRegistry::getName));

        int i = 0;
        int j = 0;

        while (i < registry.length || j < files.length) {
            if (i == registry.length) {
                // add a new file
                handleAddingFromSaf(context, files[j], targetDir, filesRegistryMap);
                j++;
                continue;
            }
            if (j == files.length) {
                // remove file
                handleRemovingFromSaf(registry[i].getName(), targetDir, filesRegistryMap);
                i++;
                continue;
            }
            if (!registry[i].getName().equals(files[j].getFileName())) {
                // to decide later add of remove
                if (registry[i].getName().compareTo(files[j].getFileName()) > 0) {
                    handleAddingFromSaf(context, files[j], targetDir, filesRegistryMap);
                    j++;
                } else {
                    handleRemovingFromSaf(registry[i].getName(), targetDir, filesRegistryMap);
                    i++;
                }
                continue;
            }
            // apply changes fo current file of directory if any
            handleChangesFromSaf(context, files[j], targetDir, registry[i]);
            j++;
            i++;
        }
    }

    private void handleAddingFromSaf(Context context, SAFFileInfo file, File targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        String filename = file.getFileName();
        if (".git".equals(filename)) {
            return;
        }
        File targetFile = new File(targetDir, filename);
        FileRegistry fileInfo;
        if (file.isDirectory()) {
            fileInfo = new FileRegistry(filename, FileRegistry.NODE_FOLDER);
            // should we put it inside?
            targetFile.mkdir();
            transferChangesRecursivelyFromSaf(context, file.getUri(), targetFile, fileInfo.getFiles());
        } else {
            fileInfo = new FileRegistry(filename, FileRegistry.NODE_FILE);
            copyFileFromSaf(context, file, targetFile, fileInfo);
        }
        filesRegistryMap.put(filename, fileInfo);
    }

    private void handleRemovingFromSaf(String filename, File targetDir, TreeMap<String, FileRegistry> filesRegistryMap) {
        File targetFile = new File(targetDir, filename);
        if (targetFile.isDirectory()) {
            deleteDirectoryRecursively(targetFile);
        } else {
            targetFile.delete();
        }
        filesRegistryMap.remove(filename);
    }

    private void handleChangesFromSaf(Context context, SAFFileInfo file, File targetDir, FileRegistry fileInfo) {
        String filename = fileInfo.getName();
        if (".git".equals(filename)) {
            return;
        }
        File targetFile = new File(targetDir, filename);
        if (fileInfo.getType() == FileRegistry.NODE_FOLDER) {
            // Create a corresponding subdirectory in the target directory
            // Recursively copy files in this subdirectory
            transferChangesRecursivelyFromSaf(context, file.getUri(), targetFile, fileInfo.getFiles());
        } else if (file.isFile()) {
            if (isFileUnchangedBasedOnStoredLocalTime(file, fileInfo)) {
                // Log.d(TAG, "Skipping unchanged file: " + targetFile.getAbsolutePath());
                // no changed needed
                return;
            }
            // Copy the file to the target directory
            copyFileFromSaf(context, file, targetFile, fileInfo);
        }
    }

    private boolean isFileUnchangedBasedOnStoredLocalTime(SAFFileInfo sourceFile, FileRegistry targetFile) {
        if (sourceFile.getSize() != targetFile.getSize()) {
            return false;
        }
        // SAF doesn't provide a direct last-modified timestamp, so rely on metadata
        long sourceLastModified = sourceFile.getModificationTime();
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

    private void copyFileFromSaf(Context context, SAFFileInfo sourceFile, File targetFile, FileRegistry fileInfo) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceFile.getUri());
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            // targetFile.setLastModified(sourceFile.lastModified());
            // Log.d(TAG, "Copied file: " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileInfo.setSafModificationTime(sourceFile.getModificationTime());
        fileInfo.setLocalModificationTime(targetFile.lastModified());
        fileInfo.setSize(targetFile.length());
    }

    private boolean deleteDirectoryRecursively(File directory) {
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

    private boolean deleteDirectoryRecursively(DocumentFile directory) {
        if (directory != null && directory.isDirectory()) {
            DocumentFile[] files = directory.listFiles();
            if (files != null) {
                for (DocumentFile file : files) {
                    if (!deleteDirectoryRecursively(file)) {
                        return false;
                    }
                }
            }
        }
        return directory != null && directory.delete();
    }
}
