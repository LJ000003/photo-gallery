package com.hape.photogallery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExifDataRepository extends JpaRepository<ExifData, Long> {

    Optional<ExifData> findByPhoto_Id(Long photoId);

    void deleteByPhoto_IdIn(List<Long> photoIds);

    // === 时间线（分页） ===

    @Query(value = "SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL",
           countQuery = "SELECT COUNT(e) FROM ExifData e WHERE e.dateTaken IS NOT NULL")
    Page<ExifData> findWithDateTakenAndPhotoDesc(Pageable pageable);

    @Query(value = "SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL",
           countQuery = "SELECT COUNT(e) FROM ExifData e WHERE e.dateTaken IS NOT NULL")
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
