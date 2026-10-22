package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperienceTemplateRepository extends JpaRepository<ExperienceTemplate, UUID> {

    // What an org can see: the global templates plus its own. Another org's templates are simply not found.
    @Query("SELECT t FROM ExperienceTemplate t WHERE t.orgId IS NULL OR t.orgId = :orgId")
    List<ExperienceTemplate> findVisibleTo(@Param("orgId") UUID orgId);

    @Query("SELECT t FROM ExperienceTemplate t WHERE (t.orgId IS NULL OR t.orgId = :orgId) AND t.type = :type")
    List<ExperienceTemplate> findVisibleToByType(@Param("orgId") UUID orgId, @Param("type") ExperienceTemplateType type);

    @Query("SELECT t FROM ExperienceTemplate t WHERE t.id = :id AND (t.orgId IS NULL OR t.orgId = :orgId)")
    Optional<ExperienceTemplate> findByIdVisibleTo(@Param("id") UUID id, @Param("orgId") UUID orgId);
}
