package com.museotek.box.application.publish;

import com.museotek.box.application.box.BoxStartCards;
import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.application.experience.GetExperienceQuery;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes an experience: saves a frozen copy of the current draft as the next version, which every
 * Box it is assigned to then plays. Org managers only, like assigning. The draft must have no gaps
 * ({@link ExperienceGaps}), and its start card must not start another experience on the same Boxes
 * ({@link BoxStartCards}). Publishing a draft that hasn't changed since the last publish is a no-op.
 * Either way the answer carries {@link PublishWarnings}, which never block.
 */
@Service
public class PublishExperienceUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final OrgAccessGuard orgAccessGuard;
    private final ProjectRepository projectRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final GetExperienceQuery getExperienceQuery;
    private final ExperienceGaps experienceGaps;
    private final BoxStartCards boxStartCards;
    private final PublishedContentMapper contentMapper;
    private final PublishWarnings publishWarnings;
    private final Clock clock;

    public PublishExperienceUseCase(
            ProjectAccessGuard projectAccessGuard,
            OrgAccessGuard orgAccessGuard,
            ProjectRepository projectRepository,
            PublishedExperienceRepository publishedExperienceRepository,
            GetExperienceQuery getExperienceQuery,
            ExperienceGaps experienceGaps,
            BoxStartCards boxStartCards,
            PublishedContentMapper contentMapper,
            PublishWarnings publishWarnings,
            Clock clock
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.orgAccessGuard = orgAccessGuard;
        this.projectRepository = projectRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.getExperienceQuery = getExperienceQuery;
        this.experienceGaps = experienceGaps;
        this.boxStartCards = boxStartCards;
        this.contentMapper = contentMapper;
        this.publishWarnings = publishWarnings;
        this.clock = clock;
    }

    @Transactional
    public PublishStatus execute(UUID projectId) {
        CatalogueProjectDto catalogueProject = projectAccessGuard.requireAccess(projectId);
        UUID orgId = UUID.fromString(catalogueProject.orgId());
        orgAccessGuard.requireManager(orgId);

        // Locked first, so a save or another publish can't change the draft while it is copied.
        Project project = projectRepository.findByIdForUpdate(projectId).orElseThrow();
        Optional<PublishedExperience> latest = publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId);
        if (latest.isPresent() && latest.get().getDocVersion() == project.getDocVersion()) {
            return PublishStatus.of(latest.get(), project, publishWarnings.find(orgId));
        }

        List<String> gaps = experienceGaps.find(projectId);
        if (!gaps.isEmpty()) {
            throw new ExperienceNotReadyException("Experience " + projectId + " has gaps and can't be published yet", gaps);
        }

        ExperienceView view = getExperienceQuery.execute(projectId);
        PublishedContent content = contentMapper.toContent(view);
        // No gaps means there is a start scene with a start card.
        Long startCardTypeId = content.scenes().stream()
                .filter(PublishedContent.Scene::isStart)
                .findFirst().orElseThrow()
                .initCardTypeId();
        boxStartCards.requireFreeOnAssignedBoxes(projectId, startCardTypeId);

        int version = latest.map(copy -> copy.getVersion() + 1).orElse(1);
        PublishedExperience copy = new PublishedExperience(project, version, catalogueProject.name(),
                ExperienceOutput.valueOf(content.output()), startCardTypeId, contentMapper.toJson(content),
                contentMapper.mediaIds(content), contentMapper.cardTypeIds(content),
                Instant.now(clock), CurrentPrincipal.subject().orElse(null));
        publishedExperienceRepository.save(copy);
        return PublishStatus.of(copy, project, publishWarnings.find(orgId));
    }
}
