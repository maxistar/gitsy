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
        FileUtils.saveProjectList(context, projects);
    }

    public void deleteProject(Context context, int position) {
        projects.remove(position);
        FileUtils.saveProjectList(context, projects);
    }
}
