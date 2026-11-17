package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.publish.PublishedExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublishedExperienceRepository extends JpaRepository<PublishedExperience, UUID> {
    // The copy Boxes play.
    Optional<PublishedExperience> findTopByProjectIdOrderByVersionDesc(UUID projectId);

    // The copy Boxes play, for every live experience on this box. Assigning requires a copy, so each one has one.
    @Query("""
            select pe from Box b join b.assignedProjects p, PublishedExperience pe
            where b.id = :boxId and p.deletedAt is null and pe.project = p
              and pe.version = (select max(v.version) from PublishedExperience v where v.project = p)
            order by pe.name
            """)
    List<PublishedExperience> findLatestCopiesOnBox(@Param("boxId") Long boxId);

    // Names of the other live experiences on this box whose latest copy starts with this card.
    @Query("""
            select p.name from Box b join b.assignedProjects p, PublishedExperience pe
            where b.id = :boxId and p.id <> :projectId and p.deletedAt is null
              and pe.project = p and pe.startCardTypeId = :cardTypeId
              and pe.version = (select max(v.version) from PublishedExperience v where v.project = p)
            """)
    List<String> findProjectNamesOnBoxWithStartCard(
            @Param("boxId") Long boxId, @Param("projectId") UUID projectId, @Param("cardTypeId") Long cardTypeId);

    // Names of the boxes this experience is on where another live experience's latest copy starts with this card.
    @Query("""
            select distinct b.name from Box b join b.assignedProjects mine join b.assignedProjects other,
                PublishedExperience pe
            where mine.id = :projectId and other.id <> :projectId and other.deletedAt is null
              and pe.project = other and pe.startCardTypeId = :cardTypeId
              and pe.version = (select max(v.version) from PublishedExperience v where v.project = other)
            """)
    List<String> findBoxNamesWhereStartCardIsTaken(@Param("projectId") UUID projectId, @Param("cardTypeId") Long cardTypeId);

    @Query("""
            select count(pe) > 0 from PublishedExperience pe
            where pe.startCardTypeId = :cardTypeId
              and pe.version = (select max(v.version) from PublishedExperience v where v.project = pe.project)
            """)
    boolean latestCopyStartsWith(@Param("cardTypeId") Long cardTypeId);

    // Only the latest copy counts: older versions are never played. Deleted experiences count, like their drafts do.
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM published_experiences pe
                WHERE pe.media_ids @> jsonb_build_array(CAST(:mediaId AS text))
                  AND pe.version = (SELECT max(v.version) FROM published_experiences v WHERE v.project_id = pe.project_id))
            """, nativeQuery = true)
    boolean latestCopyUsesMedia(@Param("mediaId") String mediaId);

    // Whether the latest copy of a live experience on this box uses the media: what a display of the box may load.
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM box_projects bp
                JOIN projects p ON p.id = bp.project_id AND p.deleted_at IS NULL
                JOIN published_experiences pe ON pe.project_id = p.id
                WHERE bp.box_id = :boxId
                  AND pe.media_ids @> jsonb_build_array(CAST(:mediaId AS text))
                  AND pe.version = (SELECT max(v.version) FROM published_experiences v WHERE v.project_id = p.id))
            """, nativeQuery = true)
    boolean latestCopyOnBoxUsesMedia(@Param("boxId") Long boxId, @Param("mediaId") String mediaId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM published_experiences pe
                WHERE pe.card_type_ids @> jsonb_build_array(CAST(:cardTypeId AS bigint))
                  AND pe.version = (SELECT max(v.version) FROM published_experiences v WHERE v.project_id = pe.project_id))
            """, nativeQuery = true)
    boolean latestCopyUsesCardType(@Param("cardTypeId") Long cardTypeId);
}
