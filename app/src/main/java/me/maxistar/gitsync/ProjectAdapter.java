package me.maxistar.gitsync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {
    private List<ProjectModel> projectList;
    private OnProjectListener onProjectListener;

    public interface OnProjectListener {
        void onProjectDelete(int position);
        // void onProjectSynchronize(int position);
    }

    public ProjectAdapter(List<ProjectModel> projectList, OnProjectListener listener) {
        this.projectList = projectList;
        this.onProjectListener = listener;
    }

    public void setProjects(List<ProjectModel> projects) {
        this.projectList = new ArrayList<>(projects);
        notifyDataSetChanged(); // Important to notify the adapter of data change
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ProjectViewHolder(itemView, onProjectListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        ProjectModel project = projectList.get(position);
        holder.textView.setText(project.getRepoUrl());
        holder.statusView.setText(statusToString(project.getStatus()));
        String lastChanges = formatLastSync(project.getLastSync());
        holder.filesView.setText("files: " + project.getNumberFiles() + lastChanges);
    }

    private String formatLastSync(long lastSync) {
        long now = System.currentTimeMillis();
        if (now - lastSync < 60 * 1000) {
            return ", " + Math.round((now - lastSync) / 1000.0) + "s ago";
        } else if (now - lastSync < 3600 * 1000) {
            return ", " + Math.round((now - lastSync) / (60.0 * 1000)) + "m ago";
        } else if (now - lastSync < 24 * 3600 * 1000) {
            return ", " + Math.round((now - lastSync) / (3600.0 * 1000)) + "h ago";
        } else {
            return ", " + Math.round((now - lastSync) / (24.0 * 3600 * 1000)) + "d ago";
        }

    }

    private String statusToString(int status) {
        if (status == ProjectModel.STATUS_READY) {
            return " ready";
        } else if (status == ProjectModel.STATUS_TO_CLONE) {
            return " ready to clone";
        } else if (status == ProjectModel.STATUS_CLONING) {
            return " cloning";
        } else if (status == ProjectModel.STATUS_CLONING_ERROR) {
            return " cloning error";
        } else if (status == ProjectModel.STATUS_TO_SYNC) {
            return " ready to sync";
        } else if (status == ProjectModel.STATUS_SYNC_IN_PROGRESS) {
            return " sync in progress";
        } else if (status == ProjectModel.STATUS_SYNC_ERROR) {
            return " sync error";
        }
        return " something weird";
    }

    @Override
    public int getItemCount() {
        return projectList.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        TextView textView;
        TextView statusView;

        TextView filesView;
        OnProjectListener onProjectListener;

        public ProjectViewHolder(View itemView, OnProjectListener listener) {
            super(itemView);
            textView = itemView.findViewById(R.id.item_title);
            statusView = itemView.findViewById(R.id.item_status);
            filesView = itemView.findViewById(R.id.item_files);
            this.onProjectListener = listener;
            itemView.setOnCreateContextMenuListener(this); // Register for context menu
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.add(this.getAdapterPosition(), 0, 0, "Delete");
            menu.add(this.getAdapterPosition(), 1, 1, "Synchronize");
        }
    }
}

