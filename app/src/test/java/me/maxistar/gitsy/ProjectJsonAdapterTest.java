package me.maxistar.gitsy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ProjectJsonAdapterTest {
    private final ProjectJsonAdapter.CredentialCodec codec = new ProjectJsonAdapter.CredentialCodec() {
        @Override
        public String encrypt(String value, String associatedData) {
            return "encoded:" + value + ":" + associatedData;
        }

        @Override
        public String decrypt(String value, String associatedData) {
            String suffix = ":" + associatedData;
            if (!value.startsWith("encoded:") || !value.endsWith(suffix)) {
                throw new IllegalArgumentException("Invalid test credential");
            }
            return value.substring("encoded:".length(), value.length() - suffix.length());
        }
    };

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ProjectModel.class, new ProjectJsonAdapter(codec))
            .create();

    @Test
    public void currentProjectRoundTripPreservesPersistedFields() {
        ProjectModel project = new ProjectModel(
                "https://example.invalid/notes.git",
                "fixture-user",
                "test-token",
                "content://test/tree/notes");
        project.folderName = "project_fixture";
        project.lastSync = 123456789L;
        project.numberFiles = 42;
        project.status = ProjectModel.STATUS_READY;

        ProjectModel restored = gson.fromJson(gson.toJson(project), ProjectModel.class);

        assertEquals(project.folderName, restored.folderName);
        assertEquals(project.folderUri, restored.folderUri);
        assertEquals(project.repoUrl, restored.repoUrl);
        assertEquals(project.userName, restored.userName);
        assertEquals(project.password, restored.password);
        assertEquals(project.lastSync, restored.lastSync);
        assertEquals(project.numberFiles, restored.numberFiles);
        assertEquals(project.status, restored.status);
    }

    @Test
    public void checkedInCurrentFixtureCanBeRead() throws Exception {
        ProjectModel restored = gson.fromJson(resource("projects/current-project.json"), ProjectModel.class);
        assertEquals("project_fixture", restored.folderName);
        assertEquals("test-token", restored.password);
        assertEquals(42, restored.numberFiles);
        assertEquals(ProjectModel.STATUS_READY, restored.status);
    }

    @Test
    public void legacyFixtureUsesBackwardCompatibleDefaults() throws Exception {
        ProjectModel restored = gson.fromJson(resource("projects/legacy-project.json"), ProjectModel.class);
        assertNotNull(restored.folderName);
        assertEquals(0L, restored.lastSync);
        assertEquals(0, restored.numberFiles);
        assertEquals(ProjectModel.STATUS_TO_CLONE, restored.status);
        assertEquals("", restored.password);
    }

    @Test
    public void corruptCredentialFailsWithoutIncludingCredentialMaterial() {
        String sensitiveBlob = "do-not-echo-this-blob";
        String json = "{\"folderUri\":\"content://test/tree/x\","
                + "\"repoUrl\":\"https://example.invalid/x.git\","
                + "\"userName\":\"user\",\"password\":\"" + sensitiveBlob + "\"}";

        try {
            gson.fromJson(json, ProjectModel.class);
            fail("Expected corrupt credential to fail");
        } catch (JsonParseException expected) {
            assertFalse(String.valueOf(expected.getMessage()).contains(sensitiveBlob));
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = ProjectJsonAdapterTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull("Missing fixture " + name, input);
            byte[] bytes = new byte[8192];
            int length = input.read(bytes);
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        }
    }
}
