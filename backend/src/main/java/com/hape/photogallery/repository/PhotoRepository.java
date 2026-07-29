package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Photo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    @Query("SELECT DISTINCT p FROM Photo p WHERE p.category.id IN :ids")
    Page<Photo> findByCategoryIds(@Param("ids") List<Long> ids, Pageable pageable);

    @Query(value = "SELECT DISTINCT p FROM Photo p JOIN p.tags t WHERE t.id IN :ids",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Photo p JOIN p.tags t WHERE t.id IN :ids")
    Page<Photo> findByTagIds(@Param("ids") List<Long> ids, Pageable pageable);

    @Query(value = "SELECT DISTINCT p FROM Photo p JOIN p.tags t WHERE p.category.id IN :catIds AND t.id IN :tagIds",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Photo p JOIN p.tags t WHERE p.category.id IN :catIds AND t.id IN :tagIds")
    Page<Photo> findByCategoryIdsAndTagIds(@Param("catIds") List<Long> catIds,
                                            @Param("tagIds") List<Long> tagIds,
                                            Pageable pageable);

    Page<Photo> findByIdIn(List<Long> ids, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Photo> search(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Photo p JOIN p.albums a WHERE a.id = :albumId")
    Page<Photo> findByAlbumId(@Param("albumId") Long albumId, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.albums IS EMPTY")
    Page<Photo> findUnassigned(Pageable pageable);

    @Query(nativeQuery = true, value = "SELECT * FROM photos WHERE id = ?1 AND deleted_at IS NOT NULL")
    Optional<Photo> findDeletedById(Long id);

    @Query(nativeQuery = true, value = "SELECT * FROM photos WHERE deleted_at IS NOT NULL AND deleted_at < ?1")
    List<Photo> findDeletedBefore(LocalDateTime threshold);

    @Query(nativeQuery = true,
           value = "SELECT * FROM photos WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC",
           countQuery = "SELECT COUNT(*) FROM photos WHERE deleted_at IS NOT NULL")
    Page<Photo> findDeleted(Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.processingStatus = :status")
    List<Photo> findByProcessingStatus(@Param("status") String status);
}
