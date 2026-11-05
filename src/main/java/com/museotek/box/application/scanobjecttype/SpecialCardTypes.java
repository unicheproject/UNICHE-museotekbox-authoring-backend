package com.museotek.box.application.scanobjecttype;

import com.museotek.box.domain.scanobject.ExitCardTypeTakenException;
import com.museotek.box.domain.scanobject.InvalidScanObjectTypeException;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeInUseException;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Component;

/**
 * Rules for the two special card types, checked when a type is created or its flags change.
 * Universal (Right/Wrong, answers A/B/C) works in any experience, so it can't pick one as its start
 * card. Exit ends any visit, so it can't be a start card or appear in a rule, and an org has one.
 * Published copies are checked here because Boxes play them now; drafts are checked at publish
 * ({@code ExperienceGaps}).
 */
@Component
public class SpecialCardTypes {

    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;

    public SpecialCardTypes(ScanObjectTypeRepository scanObjectTypeRepository,
                            PublishedExperienceRepository publishedExperienceRepository) {
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
    }

    // wasUniversal / wasExit: the flags before this change (false on create).
    public void check(ScanObjectType type, boolean wasUniversal, boolean wasExit) {
        if (type.isUniversal() && type.isExit()) {
            throw new InvalidScanObjectTypeException("A card type can't be both universal and the Exit card");
        }
        if (type.isExit() && !wasExit) {
            if (scanObjectTypeRepository.existsByOrgIdAndExitTrue(type.getOrgId())) {
                throw new ExitCardTypeTakenException("This organisation already has an Exit card type");
            }
            if (type.getId() != null && publishedExperienceRepository.latestCopyUsesCardType(type.getId())) {
                throw new ScanObjectTypeInUseException("Card type " + type.getId()
                        + " is used by a published experience, so it can't become the Exit card");
            }
        }
        if (type.isUniversal() && !wasUniversal && type.getId() != null
                && publishedExperienceRepository.latestCopyStartsWith(type.getId())) {
            throw new ScanObjectTypeInUseException("Card type " + type.getId()
                    + " starts a published experience, so it can't become universal");
        }
    }
}
