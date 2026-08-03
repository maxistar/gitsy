package me.maxistar.gitsy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class TestFiles {
    private TestFiles() {}

    static File write(File root, String relativePath, String contents) throws IOException {
        File file = new File(root, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create fixture directory: " + parent);
        }
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    static String read(File root, String relativePath) throws IOException {
        return new String(Files.readAllBytes(new File(root, relativePath).toPath()), StandardCharsets.UTF_8);
    }
}
