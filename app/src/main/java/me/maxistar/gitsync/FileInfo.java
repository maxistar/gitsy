package me.maxistar.gitsync;

import android.net.Uri;

public class FileInfo {

    public static final int TYPE_FILE = 0;
    public static final int TYPE_DIRECTORY = 1;
    public static final int TYPE_OTHER = 2;

    private String fileName;
    private long modificationTime;
    private long size;
    private Uri uri;
    private int fileType; // 0 - file, 1 - directory, 3 - other

    // Constructor
    public FileInfo(String fileName, long modificationTime, long size, Uri uri, int fileType) {
        this.fileName = fileName;
        this.modificationTime = modificationTime;
        this.size = size;
        this.uri = uri;
        this.fileType = fileType;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(long modificationTime) {
        this.modificationTime = modificationTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(int fileType) {
        this.fileType = fileType;
    }

    public boolean isDirectory() {
        return this.fileType == TYPE_DIRECTORY;
    }

    public boolean isFile() {
        return this.fileType == TYPE_FILE;
    }

}
