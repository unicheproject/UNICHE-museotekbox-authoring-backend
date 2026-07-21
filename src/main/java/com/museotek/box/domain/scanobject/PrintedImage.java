package com.museotek.box.domain.scanobject;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "printed_images")
@DiscriminatorValue("PRINTED_IMAGE")
@Getter
@Setter
@NoArgsConstructor
public class PrintedImage extends ScanObject {

    @Column(nullable = false)
    private String imageUrl;
}
