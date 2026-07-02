package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExifDataRepository extends JpaRepository<ExifData, Long> {

    Optional<ExifData> findByPhoto_Id(Long photoId);

    @Query("SELECT e FROM ExifData e WHERE e.dateTaken IS NOT NULL ORDER BY e.dateTaken DESC")
    List<ExifData> findWithDateTaken();

    @Query("SELECT e FROM ExifData e WHERE e.latitude IS NOT NULL AND e.longitude IS NOT NULL AND NOT (e.latitude = 0 AND e.longitude = 0)")
    List<ExifData> findWithGps();

    @Query("SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL ORDER BY e.dateTaken DESC")
    List<ExifData> findWithDateTakenAndPhotoDesc();

    @Query("SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.dateTaken IS NOT NULL ORDER BY e.dateTaken ASC")
    List<ExifData> findWithDateTakenAndPhotoAsc();

    @Query("SELECT e FROM ExifData e JOIN FETCH e.photo p WHERE e.latitude IS NOT NULL AND e.longitude IS NOT NULL AND NOT (e.latitude = 0 AND e.longitude = 0)")
    List<ExifData> findWithGpsAndPhoto();
}
