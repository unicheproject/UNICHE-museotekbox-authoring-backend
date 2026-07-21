package com.museotek.box.domain.scanobject;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "three_d_printed_objects")
@DiscriminatorValue("THREE_D_PRINTED_OBJECT")
@Getter
@Setter
@NoArgsConstructor
public class ThreeDPrintedObject extends ScanObject {

    @Column(nullable = false)
    private String modelRef;
}
