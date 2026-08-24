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
        project.cloneFailureCategory = CloneFailureCategory.AUTHENTICATION;

        ProjectModel restored = gson.fromJson(gson.toJson(project), ProjectModel.class);

        assertEquals(project.folderName, restored.folderName);
        assertEquals(project.folderUri, restored.folderUri);
        assertEquals(project.repoUrl, restored.repoUrl);
        assertEquals(project.userName, restored.userName);
        assertEquals(project.password, restored.password);
        assertEquals(ProjectAuthenticationType.HTTPS, restored.authenticationType);
        assertEquals(project.lastSync, restored.lastSync);
        assertEquals(project.numberFiles, restored.numberFiles);
        assertEquals(project.status, restored.status);
        assertEquals(CloneFailureCategory.AUTHENTICATION,
                restored.cloneFailureCategory);
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
        assertEquals(ProjectAuthenticationType.HTTPS, restored.authenticationType);
        assertEquals(CloneFailureCategory.NONE, restored.cloneFailureCategory);
    }

    @Test
    public void sshProjectRoundTripContainsNoKeyReferenceOrSecret() {
        ProjectModel project = new ProjectModel(
                "ssh://git@example.invalid:2222/notes.git",
                "git", "must-not-be-serialized", "content://test/tree/ssh");
        project.useSshKeyAuthentication("git", 1022);

        String json = gson.toJson(project);
        ProjectModel restored = gson.fromJson(json, ProjectModel.class);

        assertEquals(ProjectAuthenticationType.SSH_KEY, restored.authenticationType);
        assertEquals(1022, restored.sshPort);
        assertNull(restored.password);
        assertFalse(json.contains("sshKeyId"));
        assertFalse(json.contains("must-not-be-serialized"));
        assertFalse(json.contains("PRIVATE KEY"));
    }

    @Test
    public void unknownAuthenticationTypeFailsClosed() {
        String json = "{\"folderUri\":\"content://test/tree/x\","
                + "\"repoUrl\":\"https://example.invalid/x.git\","
                + "\"userName\":\"user\",\"authenticationType\":\"magic\"}";
        try {
            gson.fromJson(json, ProjectModel.class);
            fail("Expected unknown authentication type to fail");
        } catch (JsonParseException expected) {
            assertFalse(String.valueOf(expected.getMessage()).contains("PRIVATE KEY"));
        }
    }

    @Test
    public void sshProjectWithoutStoredPortDefaultsTo22() {
        String json = "{\"folderUri\":\"content://test/tree/x\","
                + "\"repoUrl\":\"git@example.invalid:maxim/x.git\","
                + "\"userName\":\"git\",\"authenticationType\":\"ssh_key\"}";
        assertEquals(22, gson.fromJson(json, ProjectModel.class).getSshPort());
    }

    @Test
    public void existingMismatchedSshFallbacksRemainReadableButUrlWinsAtRuntime() throws Exception {
        String json = "{\"folderUri\":\"content://test/tree/x\","
                + "\"repoUrl\":\"ssh://git@example.invalid:1022/maxim/x.git\","
                + "\"userName\":\"stale-user\",\"sshPort\":2200,"
                + "\"authenticationType\":\"ssh_key\"}";
        ProjectModel restored = gson.fromJson(json, ProjectModel.class);
        assertEquals("stale-user", restored.getUserName());
        assertEquals(2200, restored.getSshPort());
        SshConnectionResolution effective = GitService.resolveSsh(restored);
        assertEquals("git", effective.getUsername());
        assertEquals(1022, effective.getPort());
        assertEquals("ssh://git@example.invalid:1022/maxim/x.git",
                effective.getCanonicalUri().toString());
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

    @Test
    public void unknownCloneFailureCategoryFallsBackToNone() {
        String json = "{\"folderUri\":\"content://test/tree/x\","
                + "\"repoUrl\":\"https://example.invalid/x.git\","
                + "\"userName\":\"user\",\"cloneFailureCategory\":\"future-value\"}";
        assertEquals(CloneFailureCategory.NONE,
                gson.fromJson(json, ProjectModel.class).getCloneFailureCategory());
    }

    @Test
    public void noneCloneFailureIsOmittedAndClearsAfterRoundTrip() {
        ProjectModel project = new ProjectModel("https://example.invalid/x.git",
                "user", "token", "content://test/tree/x");
        project.setCloneFailureCategory(CloneFailureCategory.NONE);
        String json = gson.toJson(project);
        assertFalse(json.contains("cloneFailureCategory"));
        assertEquals(CloneFailureCategory.NONE,
                gson.fromJson(json, ProjectModel.class).getCloneFailureCategory());
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
