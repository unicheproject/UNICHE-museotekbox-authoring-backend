package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.application.experience.GetExperienceQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/experience")
public class ExperienceController {

    private final GetExperienceQuery getExperienceQuery;

    public ExperienceController(GetExperienceQuery getExperienceQuery) {
        this.getExperienceQuery = getExperienceQuery;
    }

    @GetMapping
    public ResponseEntity<ExperienceResponse> getExperience(@PathVariable UUID projectId) {
        ExperienceView view = getExperienceQuery.execute(projectId);
        ExperienceResponse response = ExperienceResponse.from(view);
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(response);
    }
}
