package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Album;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    @Query(nativeQuery = true, value = "SELECT * FROM albums WHERE id = ?1 AND deleted_at IS NOT NULL")
    Optional<Album> findDeletedById(Long id);

    @Query(nativeQuery = true, value = "SELECT * FROM albums WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    List<Album> findDeleted();
}
