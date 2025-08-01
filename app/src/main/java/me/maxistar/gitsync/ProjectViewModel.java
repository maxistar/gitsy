package me.maxistar.gitsync;

import android.content.Context;
import android.content.Intent;

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
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());
        projects.setValue(currentProjects);
    }

    public void updateProjects() {
        projects.setValue(new ArrayList<>(repository.getProjects()));
    }

    public void saveProjects(Context context) {
        repository.saveProjects(context);
    }

    public void deleteProject(Context context, int position) {
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());
        if (position >= 0 && position < currentProjects.size()) {
            repository.deleteProject(context, position);
            projects.setValue(repository.getProjects());
        }
    }

    public void syncProject(Context context, int position) {
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());
        if (position >= 0 && position < currentProjects.size()) {
            ProjectModel model = currentProjects.get(position);
            model.setStatus(ProjectModel.STATUS_TO_SYNC);
            repository.saveProjects(context);
            projects.setValue(repository.getProjects());
        }
    }

    public void syncAllProjects(Context context) {
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());
        for (ProjectModel model : currentProjects) {
            model.setStatus(ProjectModel.STATUS_TO_SYNC);
        }
        repository.saveProjects(context);
        projects.setValue(repository.getProjects());
    }


    public void syncAllProjectsOlderThanHour(Context context) {
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());
        long currentTime = System.currentTimeMillis();
        boolean triggerService = false;
        for (ProjectModel model : currentProjects) {
            if (currentTime - model.getLastSync() > 60 * 60 * 1000) {
                model.setStatus(ProjectModel.STATUS_TO_SYNC);
                triggerService = true;
            }
        }
        if (triggerService) {
            repository.saveProjects(context);
            projects.setValue(repository.getProjects());

            Intent startIntent = new Intent(context, ProjectService.class);
            context.startService(startIntent);
        }
    }

    public boolean allElementsAreReady() {
        List<ProjectModel> currentProjects = repository.getProjects();
        for (ProjectModel model : currentProjects) {
            if (model.getStatus() == ProjectModel.STATUS_TO_SYNC || model.getStatus() == ProjectModel.STATUS_SYNC_IN_PROGRESS) {
                return false;
            }
        }
        return true;
    }

    public boolean workInProgress() {
        return !allElementsAreReady();
    }

    public void resetProjectsStatus(Context applicationContext) {
        List<ProjectModel> currentProjects = new ArrayList<>(repository.getProjects());

        for (ProjectModel model : currentProjects) {
                model.setStatus(ProjectModel.STATUS_READY);
        }
        repository.saveProjects(applicationContext);
        projects.setValue(repository.getProjects());

    }
}
