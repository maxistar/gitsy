package me.maxistar.gitsy;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProjectViewModel extends ViewModel {
    private final MutableLiveData<List<ProjectModel>> projects;
    private final MutableLiveData<Boolean> projectsLoaded = new MutableLiveData<>(false);
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
            projectsLoaded.postValue(true);
        }).start();
    }

    public LiveData<List<ProjectModel>> getProjects() {
        return projects;
    }

    public LiveData<Boolean> getProjectsLoaded() {
        return projectsLoaded;
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


    public void syncProjects(Context context, List<ProjectModel> selectedProjects) {
        for (ProjectModel model : selectedProjects) {
            model.setStatus(ProjectModel.STATUS_TO_SYNC);
        }
        repository.saveProjects(context);
        projects.setValue(repository.getProjects());
    }

    public boolean allElementsAreReady() {
        List<ProjectModel> currentProjects = repository.getProjects();
        for (ProjectModel model : currentProjects) {
            if (model.getStatus() == ProjectModel.STATUS_TO_SYNC ||
                    model.getStatus() == ProjectModel.STATUS_SYNC_IN_PROGRESS ||
                    model.getStatus() == ProjectModel.STATUS_CLONING ||
                    model.getStatus() == ProjectModel.STATUS_TO_CLONE) {
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

    public void setProject(ProjectModel project, int editPosition, Context applicationContext) {
        repository.setProject(applicationContext, project, editPosition);
        projects.setValue(repository.getProjects());
    }
}
