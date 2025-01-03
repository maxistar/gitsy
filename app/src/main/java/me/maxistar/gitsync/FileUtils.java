package me.maxistar.gitsync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;

public class FileUtils {

    public static final String TAG = "GitSyncDebug";

    public static List<FileInfo> listFilesInFolder(Context context, Uri folderUri) {
        List<FileInfo> fileInfos = new ArrayList<>();

        ContentResolver resolver = context.getContentResolver();
        // String parentDocumentId = DocumentsContract.getTreeDocumentId(folderUri);
        //DocumentFile file = DocumentFile.fromTreeUri(context, folderUri);
        //Uri

        String folderDocumentId = DocumentsContract.getDocumentId(folderUri);

        // Construct the URI for the folder's children
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                folderDocumentId
        );

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        // Query files
        try (Cursor cursor = resolver.query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC" // Sort by name
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    // Extract document details
                    String documentId = cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String fileName = cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    long size = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE));
                    long modificationTime = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                    String mimeType = cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE));

                    // Determine file type
                    int fileType;

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        fileType = FileInfo.TYPE_DIRECTORY; // Directory

                    } else if (mimeType != null && mimeType.startsWith("application/") || mimeType.startsWith("text/")) {
                        fileType = FileInfo.TYPE_FILE; // File
                    } else {
                        fileType = FileInfo.TYPE_OTHER; // Other
                    }

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            folderUri,
                            documentId
                    );


                    // Build document URI


                    // Add to list
                    fileInfos.add(new FileInfo(fileName, modificationTime, size, documentUri, fileType));
                }
            }
        }

        return fileInfos;
    }

    public static void saveProjectList(Context context, ArrayList<ProjectModel> projects) {

        File outputFile = new File(context.getFilesDir(), "projects.json");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(projects, writer);
            Log.w(TAG, "Registry saved to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to save registry.");
        }

        /*Gson gson = new Gson();
        Type listType = new TypeToken<List<ProjectModel>>() {}.getType();
        String json = gson.toJson(projects, listType);

        FileOutputStream outputStream = null;
        try {
            outputStream = context.openFileOutput("projects.json", Context.MODE_PRIVATE);
            outputStream.write(json.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }*/
    }

    public static ArrayList<ProjectModel> loadProjects(Context context) {
        File inputFile = new File(context.getFilesDir(), "projects.json");
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(inputFile)) {
            Type listType = new TypeToken<ArrayList<ProjectModel>>() {}.getType();
            ArrayList<ProjectModel> result = gson.fromJson(reader, listType);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to load registry.");
            return new ArrayList<>();
        }

        /*
        FileInputStream inputStream = null;
        try {
            inputStream = context.openFileInput("projects.json");
            InputStreamReader reader = new InputStreamReader(inputStream);
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ProjectModel>>() {}.getType();
            List<ProjectModel> projects = gson.fromJson(reader, listType);
            reader.close();
            return projects != null ? projects : new ArrayList<ProjectModel>();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new ArrayList<ProjectModel>(); // Return an empty list if there was an error
        */
    }

}
