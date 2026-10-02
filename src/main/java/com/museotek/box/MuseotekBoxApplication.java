package com.museotek.box;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MuseotekBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuseotekBoxApplication.class, args);
    }

}
