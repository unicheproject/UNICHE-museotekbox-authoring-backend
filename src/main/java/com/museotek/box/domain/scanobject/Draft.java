package com.museotek.box.domain.scanobject;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drafts")
@DiscriminatorValue("DRAFT")
@Getter
@Setter
@NoArgsConstructor
public class Draft extends ScanObject {
}
