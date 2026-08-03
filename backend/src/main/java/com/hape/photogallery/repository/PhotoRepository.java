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

    @Query(nativeQuery = true,
           value = "SELECT * FROM photos WHERE MATCH(name, description) AGAINST(:q IN BOOLEAN MODE) AND deleted_at IS NULL",
           countQuery = "SELECT COUNT(*) FROM photos WHERE MATCH(name, description) AGAINST(:q IN BOOLEAN MODE) AND deleted_at IS NULL")
    Page<Photo> search(@Param("q") String q, Pageable pageable);

    /**
     * 搜索 + 标签/分类组合过滤。调用方必须传非空筛选（按场景分发到对应方法），
     * 空集合/null 集合绑定到 IN 会被 Hibernate 生成非法 SQL（IN ?）。
     */
    @Query(nativeQuery = true,
           value = """
                   SELECT DISTINCT p.* FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND pt.tag_id IN :tagIds
                   """,
           countQuery = """
                   SELECT COUNT(DISTINCT p.id) FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND pt.tag_id IN :tagIds
                   """)
    Page<Photo> searchWithTagIds(@Param("q") String q,
                                 @Param("tagIds") List<Long> tagIds,
                                 Pageable pageable);

    @Query(nativeQuery = true,
           value = """
                   SELECT * FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND p.category_id IN :catIds
                   """,
           countQuery = """
                   SELECT COUNT(*) FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND p.category_id IN :catIds
                   """)
    Page<Photo> searchWithCategoryIds(@Param("q") String q,
                                      @Param("catIds") List<Long> catIds,
                                      Pageable pageable);

    @Query(nativeQuery = true,
           value = """
                   SELECT DISTINCT p.* FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND pt.tag_id IN :tagIds
                     AND p.category_id IN :catIds
                   """,
           countQuery = """
                   SELECT COUNT(DISTINCT p.id) FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND MATCH(p.name, p.description) AGAINST(:q IN BOOLEAN MODE)
                     AND pt.tag_id IN :tagIds
                     AND p.category_id IN :catIds
                   """)
    Page<Photo> searchWithTagAndCategoryIds(@Param("q") String q,
                                            @Param("tagIds") List<Long> tagIds,
                                            @Param("catIds") List<Long> catIds,
                                            Pageable pageable);

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

    Optional<Photo> findByFileHash(String fileHash);

    /**
     * 备份导出筛选：所有参数可选，null 表示不限制。
     * albumId=0 表示未分配任何相册的照片（与 /photos 列表语义一致）。
     */
    @Query("SELECT DISTINCT p FROM Photo p WHERE "
            + "(:albumId IS NULL OR :albumId = 0 OR EXISTS (SELECT a FROM p.albums a WHERE a.id = :albumId)) "
            + "AND (:albumId IS NULL OR :albumId != 0 OR p.albums IS EMPTY) "
            + "AND (:categoryId IS NULL OR p.category.id = :categoryId) "
            + "AND (:dateFrom IS NULL OR p.createdAt >= :dateFrom) "
            + "AND (:dateTo IS NULL OR p.createdAt <= :dateTo)")
    List<Photo> findForBackup(@Param("albumId") Long albumId,
                              @Param("categoryId") Long categoryId,
                              @Param("dateFrom") LocalDateTime dateFrom,
                              @Param("dateTo") LocalDateTime dateTo);
}
