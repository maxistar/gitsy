package me.maxistar.gitsync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public class FileUtils {

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
}
