package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 单字搜索（<2 字符）：FULLTEXT 的 ngram 按双字分词，单字无法成 token，
     * fallback 到 LIKE 子串匹配。pattern 由调用方拼好并转义 LIKE 通配符。
     * 组合筛选按场景分发到对应方法（与 searchWithTagIds 等同一模式）。
     */
    @Query(nativeQuery = true,
           value = """
                   SELECT * FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                   """,
           countQuery = """
                   SELECT COUNT(*) FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                   """)
    Page<Photo> searchByLike(@Param("pattern") String pattern,
                             Pageable pageable);

    @Query(nativeQuery = true,
           value = """
                   SELECT DISTINCT p.* FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND pt.tag_id IN :tagIds
                   """,
           countQuery = """
                   SELECT COUNT(DISTINCT p.id) FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND pt.tag_id IN :tagIds
                   """)
    Page<Photo> searchByLikeWithTagIds(@Param("pattern") String pattern,
                                       @Param("tagIds") List<Long> tagIds,
                                       Pageable pageable);

    @Query(nativeQuery = true,
           value = """
                   SELECT * FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND p.category_id IN :catIds
                   """,
           countQuery = """
                   SELECT COUNT(*) FROM photos p
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND p.category_id IN :catIds
                   """)
    Page<Photo> searchByLikeWithCategoryIds(@Param("pattern") String pattern,
                                            @Param("catIds") List<Long> catIds,
                                            Pageable pageable);

    @Query(nativeQuery = true,
           value = """
                   SELECT DISTINCT p.* FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND pt.tag_id IN :tagIds
                     AND p.category_id IN :catIds
                   """,
           countQuery = """
                   SELECT COUNT(DISTINCT p.id) FROM photos p
                   JOIN photo_tags pt ON p.id = pt.photo_id
                   WHERE p.deleted_at IS NULL
                     AND (p.name LIKE :pattern OR p.description LIKE :pattern)
                     AND pt.tag_id IN :tagIds
                     AND p.category_id IN :catIds
                   """)
    Page<Photo> searchByLikeWithTagAndCategoryIds(@Param("pattern") String pattern,
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

    /**
     * 条件硬删除（purge 用）：仅当行仍处于软删状态才删除，返回影响行数。
     * 必须 native——实体级 bulk JPQL DELETE 会被 @SQLRestriction("deleted_at IS NULL") 拼 WHERE，
     * 对软删行永远 0 行；native 直接绕过。0 行 = 期间被恢复（restore 竞态），调用方跳过后续文件删除。
     */
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM photos WHERE id = ?1 AND deleted_at IS NOT NULL")
    int hardDeleteIfStillDeleted(Long id);

    @Query("SELECT p FROM Photo p WHERE p.processingStatus = :status")
    List<Photo> findByProcessingStatus(@Param("status") ProcessingStatus status);

    /** 按状态分页（迁移/启动补生成用——只处理 DONE 照片，排除 PROCESSING/FAILED） */
    Page<Photo> findByProcessingStatus(ProcessingStatus status, Pageable pageable);

    /** 按文件哈希查详情（tags/albums/exifData 预取）——dedup 路径需要完整 DTO 序列化，
     *  单行查询无分页风险，替代手工 eagerLoad hack */
    @EntityGraph(attributePaths = {"tags", "albums", "exifData"})
    Optional<Photo> findWithDetailsByFileHash(String fileHash);

    /**
     * 各相册照片数：一次分组查询替代 Album.getPhotoCount() 的整集合懒加载 N+1。
     * @SQLRestriction 自动排除软删照片；已知局限：回收站相册不计已删照片（回收站 UI 不显示计数）。
     */
    @Query("SELECT a.id, COUNT(p.id) FROM Album a LEFT JOIN a.photos p GROUP BY a.id")
    List<Object[]> countByAlbum();

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

    /* ---------- 统计聚合（@SQLRestriction 自动排除软删除；JPQL YEAR/MONTH H2/MySQL 均兼容） ---------- */

    @Query("SELECT COUNT(p) FROM Photo p")
    long countAll();

    @Query("SELECT COALESCE(SUM(p.fileSize), 0) FROM Photo p")
    long sumFileSize();

    /** 按月上传趋势：返回 [年, 月, 张数] 行，按时间升序 */
    @Query("SELECT YEAR(p.createdAt), MONTH(p.createdAt), COUNT(p) FROM Photo p "
            + "GROUP BY YEAR(p.createdAt), MONTH(p.createdAt) "
            + "ORDER BY YEAR(p.createdAt), MONTH(p.createdAt)")
    List<Object[]> countGroupedByMonth();

    /** 热门标签：返回 [名称, 颜色, 照片数] 行，按照片数降序 */
    @Query("SELECT t.name, t.color, COUNT(p.id) FROM Tag t JOIN t.photos p "
            + "GROUP BY t.id ORDER BY COUNT(p.id) DESC")
    List<Object[]> countByTag(Pageable pageable);

    /** 相册内照片 id 列表（只投影主键列，零关系加载——相册选择器预选初始化用）。
     *  显式 ORDER BY p.id：封面重选取「第一张」需要确定性行序（无 ORDER BY 时
     *  MySQL 行序无契约，同一场景封面可能漂移） */
    @Query("SELECT p.id FROM Photo p JOIN p.albums a WHERE a.id = :albumId ORDER BY p.id")
    List<Long> findPhotoIdsByAlbumId(@Param("albumId") Long albumId);

    /** 备份指纹聚合：[照片数, 最大 id, 最大创建时间]（@SQLRestriction 自动排除软删除） */
    @Query("SELECT COUNT(p), MAX(p.id), MAX(p.createdAt) FROM Photo p")
    Object[] backupAggregate();

    /** 备份指纹：回收站最新删除时间（恢复操作会改变它，native 绕过 @SQLRestriction） */
    @Query(nativeQuery = true, value = "SELECT MAX(deleted_at) FROM photos")
    LocalDateTime maxDeletedAt();
}
