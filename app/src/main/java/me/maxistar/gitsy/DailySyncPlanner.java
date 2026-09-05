package me.maxistar.gitsy;

import java.util.ArrayList;
import java.util.List;

final class DailySyncPlanner {
    private final ScheduledProjectEligibility eligibility;

    DailySyncPlanner(ScheduledProjectEligibility eligibility) {
        this.eligibility = eligibility;
    }

    List<String> eligibleProjectIds(List<ProjectModel> projects) {
        List<String> result = new ArrayList<>();
        for (ProjectModel project : projects) {
            if (eligibility.isEligible(project)) result.add(project.getFolderName());
        }
        return result;
    }
}
