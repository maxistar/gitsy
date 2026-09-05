package me.maxistar.gitsy;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ProjectRepository {
    private static ProjectRepository instance;

    public static ProjectRepository getInstance() {
        if (instance == null) {
            instance = new ProjectRepository();
        }
        return instance;
    }

    private ArrayList<ProjectModel> projects;
    private boolean loaded;

    public ProjectRepository() {
        projects = new ArrayList<ProjectModel>();
    }

    public synchronized List<ProjectModel> loadProjects(Context context) {
        projects = FileUtils.loadProjects(context);
        loaded = true;
        return projects;
    }

    public synchronized List<ProjectModel> ensureProjectsLoaded(Context context) {
        return loaded ? projects : loadProjects(context);
    }

    public synchronized List<ProjectModel> getProjects() {
        return projects;
    }

    public synchronized void addProject(Context context, ProjectModel project) {
        projects.add(project);
        saveProjects(context);
    }

    public synchronized void setProject(Context context, ProjectModel projectModel, int position) {
        projects.set(position, projectModel);
        saveProjects(context);
    }

    public synchronized void deleteProject(Context context, int position) {
        projects.remove(position);
        saveProjects(context);
    }

    public synchronized void saveProjects(Context context) {
        FileUtils.saveProjectList(context, projects);
    }

    public synchronized ProjectModel getProject(int position) {
        return projects.get(position);
    }

    public synchronized ProjectModel findProject(String folderName) {
        for (ProjectModel project : projects) {
            if (project.getFolderName().equals(folderName)) return project;
        }
        return null;
    }
}
