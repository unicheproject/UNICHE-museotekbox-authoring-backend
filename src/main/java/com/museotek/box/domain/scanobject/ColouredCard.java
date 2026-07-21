package com.museotek.box.domain.scanobject;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "coloured_cards")
@DiscriminatorValue("COLOURED_CARD")
@Getter
@Setter
@NoArgsConstructor
public class ColouredCard extends ScanObject {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardColour colour;
}
