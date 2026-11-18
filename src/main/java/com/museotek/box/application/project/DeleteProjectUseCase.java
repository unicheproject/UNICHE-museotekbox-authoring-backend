package com.museotek.box.application.project;

import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DeleteProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;
    private final BoxRepository boxRepository;
    private final ApplicationEventPublisher events;

    public DeleteProjectUseCase(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync,
                                BoxRepository boxRepository, ApplicationEventPublisher events) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
        this.boxRepository = boxRepository;
        this.events = events;
    }

    public void execute(UUID id) {
        catalogueClient.deleteProject(id);
        companionSync.softDelete(id);
        // Boxes that have it assigned stop or start playing it.
        List<Long> boxIds = boxRepository.findIdsByAssignedProject(id);
        if (!boxIds.isEmpty()) {
            events.publishEvent(new BoxesNeedSyncEvent(boxIds));
        }
    }
}
