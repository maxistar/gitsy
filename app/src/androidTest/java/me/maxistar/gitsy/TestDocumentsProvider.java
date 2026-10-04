package me.maxistar.gitsy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TestDocumentsProvider extends ContentProvider {
    static final String AUTHORITY = "me.maxistar.gitsync.test.documents";
    static final String ROOT_ID = "root";
    private File root;

    @Override
    public boolean onCreate() {
        root = new File(getContext().getCacheDir(), "documents-provider-root");
        return root.isDirectory() || root.mkdirs();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            String documentId = DocumentsContract.getDocumentId(uri);
            MatrixCursor cursor = documentCursor(projection);
            if (uri.getPathSegments().contains("children")) {
                File parent = resolve(documentId);
                File[] children = parent.listFiles();
                if (children == null) throw new FileNotFoundException("Cannot list " + documentId);
                java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
                for (File child : children) includeDocument(cursor, child, documentId(child));
            } else {
                includeDocument(cursor, resolve(documentId), documentId);
            }
            return cursor;
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        try {
            if ("android:createDocument".equals(method)) {
                Uri parentUri = extras.getParcelable("uri");
                String documentId = createDocument(
                        DocumentsContract.getDocumentId(parentUri),
                        extras.getString("mime_type"),
                        extras.getString("_display_name"));
                Bundle result = new Bundle();
                result.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId));
                return result;
            }
            if ("android:deleteDocument".equals(method)) {
                Uri uri = extras.getParcelable("uri");
                deleteDocument(DocumentsContract.getDocumentId(uri));
                return new Bundle();
            }
            return super.call(method, arg, extras);
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = resolve(parentDocumentId);
        File child = new File(parent, displayName);
        try {
            boolean created = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)
                    ? child.mkdir()
                    : child.createNewFile();
            if (!created) throw new IOException("Already exists or cannot create");
        } catch (IOException error) {
            throw fileNotFound("Could not create " + displayName, error);
        }
        return documentId(child);
    }

    private void deleteDocument(String documentId) throws FileNotFoundException {
        File file = resolve(documentId);
        if (file.equals(root) || !deleteRecursively(file)) throw new FileNotFoundException("Could not delete " + documentId);
    }

    @Override public String getType(Uri uri) {
        try {
            return resolve(DocumentsContract.getDocumentId(uri)).isDirectory()
                    ? DocumentsContract.Document.MIME_TYPE_DIR
                    : "application/octet-stream";
        } catch (FileNotFoundException error) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            deleteDocument(DocumentsContract.getDocumentId(uri));
            return 1;
        } catch (FileNotFoundException error) {
            return 0;
        }
    }

    private MatrixCursor documentCursor(String[] projection) {
        return new MatrixCursor(projectionOrDefault(projection, new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        }));
    }

    private void includeDocument(MatrixCursor cursor, File file, String documentId) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        put(row, cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        put(row, cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.equals(root) ? "root" : file.getName());
        put(row, cursor, DocumentsContract.Document.COLUMN_MIME_TYPE,
                file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream");
        int flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        if (file.isDirectory()) flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        put(row, cursor, DocumentsContract.Document.COLUMN_FLAGS, flags);
        put(row, cursor, DocumentsContract.Document.COLUMN_SIZE, file.length());
        put(row, cursor, DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    private File resolve(String documentId) throws FileNotFoundException {
        String relative = ROOT_ID.equals(documentId) ? "" : documentId.substring((ROOT_ID + "/").length());
        File file = new File(root, relative);
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!(filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator)) || !file.exists()) {
                throw new FileNotFoundException(documentId);
            }
        } catch (IOException error) {
            throw fileNotFound(documentId, error);
        }
        return file;
    }

    private String documentId(File file) throws FileNotFoundException {
        try {
            String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            return relative.isEmpty() ? ROOT_ID : ROOT_ID + "/" + relative;
        } catch (RuntimeException error) {
            throw fileNotFound(file.toString(), error);
        }
    }

    private static String[] projectionOrDefault(String[] projection, String[] defaults) {
        return projection == null ? defaults : projection;
    }

    private static void put(MatrixCursor.RowBuilder row, MatrixCursor cursor, String column, Object value) {
        if (cursor.getColumnIndex(column) >= 0) row.add(column, value);
    }

    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) if (!deleteRecursively(child)) return false;
        return file.delete();
    }

    private static FileNotFoundException fileNotFound(String message, Throwable cause) {
        FileNotFoundException result = new FileNotFoundException(message);
        result.initCause(cause);
        return result;
    }
}
