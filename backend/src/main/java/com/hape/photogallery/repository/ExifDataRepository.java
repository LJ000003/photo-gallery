package com.hape.photogallery.repository;

import com.hape.photogallery.entity.ExifData;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExifDataRepository extends JpaRepository<ExifData, Long> {

    Optional<ExifData> findByPhoto_Id(Long photoId);

    // === 时间线（分页） ===
    // 显式 ORDER BY（e.id 次级键保证分页行序确定性）：此前两查询无排序且 JPQL 相同，
    // asc/desc 走同一查询、分页首页不是最新照片（前端只传 sortOrder 不传 sort）。
    // 查询自带 ORDER BY 后 pageable sort 会被 Spring Data 忽略，天然规避未知 sort 500。
    // countQuery 显式 JOIN e.photo 并过滤软删（内容查询经 JOIN FETCH + @SQLRestriction
    // 已排除软删照片，count 不过滤则 total 虚高 → 末页空页）。

    @Query(value = "SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL "
           + "ORDER BY e.dateTaken DESC, e.id DESC",
           countQuery = "SELECT COUNT(e) FROM ExifData e JOIN e.photo p "
                        + "WHERE e.dateTaken IS NOT NULL AND p.deletedAt IS NULL")
    Page<ExifData> findWithDateTakenAndPhotoDesc(Pageable pageable);

    @Query(value = "SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL "
           + "ORDER BY e.dateTaken ASC, e.id ASC",
           countQuery = "SELECT COUNT(e) FROM ExifData e JOIN e.photo p "
                        + "WHERE e.dateTaken IS NOT NULL AND p.deletedAt IS NULL")
    Page<ExifData> findWithDateTakenAndPhotoAsc(Pageable pageable);

    // === 地图（边界框 + 数量限制） ===

    @Query("SELECT e FROM ExifData e JOIN FETCH e.photo p " +
           "WHERE e.latitude BETWEEN :swLat AND :neLat " +
           "AND e.longitude BETWEEN :swLng AND :neLng " +
           "AND NOT (e.latitude = 0 AND e.longitude = 0)")
    List<ExifData> findWithGpsInBounds(@Param("swLat") double swLat,
                                       @Param("swLng") double swLng,
                                       @Param("neLat") double neLat,
                                       @Param("neLng") double neLng,
                                       Pageable pageable);
}
