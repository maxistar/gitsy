package me.maxistar.gitsync;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.runner.RunWith;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.junit.After;

@RunWith(AndroidJUnit4.class)
public class FileStorageServiceTest {
    public static final String SOURCE_DIR_NAME = "test-source";
    private Context context;
    private FileStorageService fileStorageService;
    private File sourceDir;


    @Before
    public void setUp() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fileStorageService = new FileStorageService("test-registry.json");


        // Set up source and destination directories
        sourceDir = new File(context.getCacheDir(), SOURCE_DIR_NAME);
        fileStorageService.deleteDirectoryRecursivelyAndSaveRegistry(context, sourceDir);
        sourceDir.mkdirs();
    }

    public void createTestFile(File file, String content) throws IOException {
        //if (file.getParentFile() != null && !file.getParentFile().exists()) {
        //    file.getParentFile().mkdirs(); // Ensure parent directories exist
        //}

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
            System.out.println("File created: " + file.getAbsolutePath());
        }
    }

    public void createTestFile(DocumentFile file, String content) throws IOException {
        try (OutputStream fos = context.getContentResolver().openOutputStream(file.getUri())) {
            if (fos != null) {
                fos.write(content.getBytes());
                System.out.println("File created: " + file.getUri());
            } else {
                System.out.println("Failed to create file: " + file.getName());
            }
        }
    }

    public String getFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString().trim(); // Trim to remove the trailing newline
    }

    public String getFileContent(DocumentFile file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString().trim(); // Trim to remove the trailing newline
    }

    @After
    public void tearDown() {
        // Clean up test directories
        deleteDirectoryRecursivelyAndSaveRegistry(sourceDir);

        Uri safUri = getTestSafDirectoryUri();
        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, safUri);
        deleteDirectoryContent(pickedDir);
    }

    private void deleteDirectoryContent(DocumentFile directory) {
        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                // Recursively delete files and subdirectories
                if (!deleteDirectoryRecursivelyAndSaveRegistry(file)) {
                    return; // Stop if any deletion fails
                }
            }
        }
    }

    private boolean deleteDirectoryRecursivelyAndSaveRegistry(DocumentFile directory) {
        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                // Recursively delete files and subdirectories
                if (!deleteDirectoryRecursivelyAndSaveRegistry(file)) {
                    return false; // Stop if any deletion fails
                }
            }
        }

        // Delete the file or empty directory
        return directory != null && directory.delete();
    }

    private void deleteDirectoryRecursivelyAndSaveRegistry(File directory) {
        if (directory != null && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                deleteDirectoryRecursivelyAndSaveRegistry(file);
            }
        }
        if (directory != null) {
            directory.delete();
        }
    }

    @Test
    public void testCopyToSaf() throws IOException {
        Uri safUri = getTestSafDirectoryUri();

        createTestFile(new File(sourceDir, "file1.txt"), "some contents");
        createTestFile(new File(sourceDir, "file2.txt"), "some contents");
        File folder = new File(sourceDir, "folder");
        folder.mkdir();
        createTestFile(new File(folder, "file2.txt"), "some contents");
        System.out.println("Created 2 files");

        // Move files to SAF directory
        fileStorageService.copyToSaf(context, SOURCE_DIR_NAME, safUri);

        // Verify that files were moved
        DocumentFile safDirectory = DocumentFile.fromTreeUri(context, safUri);
        assert safDirectory != null;
        assert safDirectory.listFiles().length == 3; // Ensure all files moved
    }

    @Test
    public void testCopyFromSaf() throws IOException {
        Uri safUri = getTestSafDirectoryUri();

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, safUri);

        createTestFileInFolder(pickedDir, "filename1", "some file content 1");
        createTestFileInFolder(pickedDir, "filename2", "some file content 2");

        DocumentFile subDir = pickedDir.createDirectory("folder");
        createTestFileInFolder(subDir, "filename3", "some file content 3");

        System.out.println("Created 2 files");

        fileStorageService.copyFromSaf(context, safUri, SOURCE_DIR_NAME);

        assert sourceDir.listFiles().length == 3; // Ensure all files moved
    }

    private void createTestFileInFolder(DocumentFile pickedDir, String filename, String someFileContent) throws IOException {
        DocumentFile newFile = pickedDir.createFile("application/octet-stream", filename);
        createTestFile(newFile, someFileContent);
    }

    private Uri getTestSafDirectoryUri() {
        return Uri.parse("content://com.android.externalstorage.documents/tree/0CFA-3314%3ADocuments%2FTest03");
    }
}
