package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectAuthenticationValidatorTest {
    private final ProjectAuthenticationValidator validator = new ProjectAuthenticationValidator();

    @Test
    public void validatesHttpsAndCanonicalSshProjects() {
        ProjectModel https = project("https://example.invalid/notes.git", "user", "token");
        assertEquals(ProjectAuthenticationValidator.Result.VALID, validator.validate(https));

        ProjectModel ssh = project("ssh://git@example.invalid:1022/maxim/notes.git", "git", null);
        ssh.useSshKeyAuthentication("git");
        assertEquals(ProjectAuthenticationValidator.Result.VALID, validator.validate(ssh));
    }

    @Test
    public void rejectsAuthenticationAndUrlMismatch() {
        ProjectModel sshWithHttps = project("https://example.invalid/notes.git", "git", null);
        sshWithHttps.useSshKeyAuthentication("git");
        assertEquals(ProjectAuthenticationValidator.Result.INCOMPATIBLE_URL,
                validator.validate(sshWithHttps));

        ProjectModel httpsWithSsh = project("ssh://git@example.invalid/notes.git", "git", "token");
        assertEquals(ProjectAuthenticationValidator.Result.INCOMPATIBLE_URL,
                validator.validate(httpsWithSsh));
    }

    @Test
    public void reportsMissingHttpsSecretWhileGlobalSshKeyIsValidatedSeparately() {
        assertEquals(ProjectAuthenticationValidator.Result.MISSING_HTTPS_SECRET,
                validator.validate(project("https://example.invalid/notes.git", "user", "")));

        ProjectModel ssh = project("ssh://git@example.invalid/notes.git", "git", null);
        ssh.useSshKeyAuthentication("git");
        assertEquals(ProjectAuthenticationValidator.Result.VALID, validator.validate(ssh));
    }

    @Test
    public void scpStyleUrlUsesExplicitProjectPort() throws Exception {
        ProjectModel ssh = project("git@example.invalid:maxim/notes.git", "git", null);
        ssh.useSshKeyAuthentication("git", 1022);
        assertEquals(ProjectAuthenticationValidator.Result.VALID,
                validator.validate(ssh));
        assertEquals("ssh://git@example.invalid:1022/maxim/notes.git",
                SshRepositoryUri.parse(ssh.getRepoUrl(), ssh.getUserName(), ssh.getSshPort())
                        .toString());
    }

    @Test
    public void invalidSshPortIsRejected() {
        ProjectModel ssh = project("git@example.invalid:maxim/notes.git", "git", null);
        ssh.useSshKeyAuthentication("git", 70000);
        assertEquals(ProjectAuthenticationValidator.Result.INVALID_URL, validator.validate(ssh));
    }

    @Test
    public void urlUsernameSatisfiesSshValidationAndMissingEffectiveUsernameIsReported() {
        ProjectModel embedded = project("git@example.invalid:maxim/notes.git", "", null);
        embedded.useSshKeyAuthentication("", 22);
        assertEquals(ProjectAuthenticationValidator.Result.VALID, validator.validate(embedded));

        ProjectModel missing = project("ssh://example.invalid/maxim/notes.git", "", null);
        missing.useSshKeyAuthentication("", 22);
        assertEquals(ProjectAuthenticationValidator.Result.MISSING_USERNAME,
                validator.validate(missing));
    }

    private static ProjectModel project(String url, String user, String secret) {
        return new ProjectModel(url, user, secret, "content://notes");
    }
}
