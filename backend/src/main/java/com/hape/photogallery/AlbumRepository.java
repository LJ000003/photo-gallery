package com.hape.photogallery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByName(String name);
}
