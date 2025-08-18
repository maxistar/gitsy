package me.maxistar.gitsync;

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

    public ProjectRepository() {
        projects = new ArrayList<ProjectModel>();
    }

    public List<ProjectModel> loadProjects(Context context) {
        projects = FileUtils.loadProjects(context);
        return projects;
    }

    public List<ProjectModel> getProjects() {
        return projects;
    }

    public void addProject(Context context, ProjectModel project) {
        projects.add(project);
        saveProjects(context);
    }

    public void setProject(Context context, ProjectModel projectModel, int position) {
        projects.set(position, projectModel);
        saveProjects(context);
    }

    public void deleteProject(Context context, int position) {
        projects.remove(position);
        saveProjects(context);
    }

    public void saveProjects(Context context) {
        FileUtils.saveProjectList(context, projects);
    }

    public ProjectModel getProject(int position) {
        return projects.get(position);
    }
}
