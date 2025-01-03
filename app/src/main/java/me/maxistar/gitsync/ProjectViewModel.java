package me.maxistar.gitsync;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProjectViewModel extends ViewModel {
    private final MutableLiveData<List<ProjectModel>> projects;
    private final ProjectRepository repository;

    public ProjectViewModel() {
        repository = ProjectRepository.getInstance();
        projects = new MutableLiveData<>();
        projects.setValue(repository.getProjects());
    }

    public void loadProjects(Context context) {
        new Thread(() -> {
            List<ProjectModel> loadedProjects = repository.loadProjects(context); // Assume this is a public method now
            projects.postValue(loadedProjects);
        }).start();
    }

    public LiveData<List<ProjectModel>> getProjects() {
        return projects;
    }

    public void addProject(Context context, ProjectModel project) {
        repository.addProject(context, project);
        List<ProjectModel> currentProjects = repository.getProjects();
        projects.setValue(currentProjects);
    }

    public void updateProjects() {
        projects.postValue(new ArrayList<>(repository.getProjects()));
    }

    public void deleteProject(Context context, int position) {
        List<ProjectModel> currentProjects = repository.getProjects();
        if (position >= 0 && position < currentProjects.size()) {
            repository.deleteProject(context, position);
            projects.setValue(new ArrayList<>(repository.getProjects()));
        }
    }
}
