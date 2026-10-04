package me.maxistar.gitsy;

import org.eclipse.jgit.transport.URIish;

public final class ProjectAuthenticationValidator {
    public enum Result {
        VALID,
        MISSING_URL,
        INVALID_URL,
        INCOMPATIBLE_URL,
        MISSING_USERNAME,
        MISSING_HTTPS_SECRET
    }

    public Result validate(ProjectModel project) {
        if (isBlank(project.getRepoUrl())) return Result.MISSING_URL;

        URIish uri;
        try {
            uri = new URIish(project.getRepoUrl());
        } catch (Exception ignored) {
            return Result.INVALID_URL;
        }

        String scheme = uri.getScheme();
        if (project.getAuthenticationType() == ProjectAuthenticationType.HTTPS) {
            if (isBlank(project.getUserName())) return Result.MISSING_USERNAME;
            if (!"https".equalsIgnoreCase(scheme)) return Result.INCOMPATIBLE_URL;
            return isBlank(project.getPassword()) ? Result.MISSING_HTTPS_SECRET : Result.VALID;
        }

        try {
            SshRepositoryUri.parse(project.getRepoUrl(), project.getUserName(), project.getSshPort());
        } catch (java.net.URISyntaxException error) {
            if (String.valueOf(error.getReason()).contains("username")) {
                return Result.MISSING_USERNAME;
            }
            return project.getSshPort() < 1 || project.getSshPort() > 65535
                    ? Result.INVALID_URL : Result.INCOMPATIBLE_URL;
        }
        return Result.VALID;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
