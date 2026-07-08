package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Tag;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
}
