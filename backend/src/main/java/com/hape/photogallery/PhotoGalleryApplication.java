package com.hape.photogallery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PhotoGalleryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhotoGalleryApplication.class, args);
    }
}