package com.museotek.box.application.comment;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Open comment threads per experience of an org, for the "N comments" on the experiences list.
 * Covers exactly the experiences the caller can see: Catalogue's org project list is already
 * filtered per caller (managers get all, authors only theirs), the same rule as the list itself.
 * Every visible experience is in the answer, with 0 where nothing is open.
 */
@Service
public class CountOpenCommentsQuery {

    public record OpenCount(UUID projectId, long open) {
    }

    private final OrgAccessGuard orgAccessGuard;
    private final CatalogueClient catalogueClient;
    private final CommentRepository commentRepository;

    public CountOpenCommentsQuery(OrgAccessGuard orgAccessGuard, CatalogueClient catalogueClient, CommentRepository commentRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.catalogueClient = catalogueClient;
        this.commentRepository = commentRepository;
    }

    public List<OpenCount> execute(UUID orgId) {
        orgAccessGuard.requireAccess(orgId);

        List<CatalogueProjectDto> visibleProjects = catalogueClient.listProjectsForOrg(orgId);
        if (visibleProjects.isEmpty()) {
            return List.of();
        }
        List<UUID> projectIds = visibleProjects.stream()
                .map(project -> UUID.fromString(project.id()))
                .toList();

        Map<UUID, Long> openByProject = new HashMap<>();
        for (CommentRepository.OpenThreadCount count : commentRepository.countOpenThreads(projectIds)) {
            openByProject.put(count.getProjectId(), count.getOpenThreads());
        }
        return projectIds.stream()
                .map(projectId -> new OpenCount(projectId, openByProject.getOrDefault(projectId, 0L)))
                .toList();
    }
}
