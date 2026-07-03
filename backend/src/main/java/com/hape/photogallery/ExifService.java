package com.hape.photogallery;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class ExifService {

    private static final Logger log = LoggerFactory.getLogger(ExifService.class);

    private final ExifDataRepository exifRepo;

    public ExifService(ExifDataRepository exifRepo) {
        this.exifRepo = exifRepo;
    }

    public ExifData extractAndSave(Photo photo, Path filePath) {
        String contentType = photo.getContentType();
        if (contentType == null) return null;

        String lower = contentType.toLowerCase();
        if (!lower.equals("image/jpeg") && !lower.equals("image/webp")) return null;

        ExifData exif = exifRepo.findByPhoto_Id(photo.getId()).orElse(new ExifData());
        exif.setPhoto(photo);

        try (InputStream in = Files.newInputStream(filePath)) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);

            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                exif.setDateTaken(parseDate(subIfd));
                exif.setFocalLength(parseFocalLength(subIfd));
                exif.setAperture(parseAperture(subIfd));
                exif.setShutterSpeed(parseShutterSpeed(subIfd));
                exif.setIso(parseIso(subIfd));
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                String model = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
                if (model != null && !model.isBlank()) exif.setCameraModel(model.trim());
            }

            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null && gps.getGeoLocation() != null) {
                double lat = gps.getGeoLocation().getLatitude();
                double lng = gps.getGeoLocation().getLongitude();
                // 排除全零坐标（部分相机/软件会写入空 GPS 标签导致落入 Null Island）
                if (Math.abs(lat) > 0.0001 || Math.abs(lng) > 0.0001) {
                    exif.setLatitude(lat);
                    exif.setLongitude(lng);
                }
            }

        } catch (ImageProcessingException | IOException e) {
            log.warn("Failed to extract EXIF for photo {}: {}", photo.getId(), e.getMessage());
        }

        if (hasAnyData(exif)) {
            return exifRepo.save(exif);
        }
        return null;
    }

    public int extractForExisting(java.util.List<Photo> photos, Path uploadDir) {
        int count = 0;
        for (Photo photo : photos) {
            Path filePath = uploadDir.resolve(photo.getFileName());
            if (!Files.exists(filePath)) continue;
            ExifData result = extractAndSave(photo, filePath);
            if (result != null) count++;
        }
        return count;
    }

    private LocalDateTime parseDate(ExifSubIFDDirectory dir) {
        int[] tags = {ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED};
        for (int tag : tags) {
            String val = dir.getString(tag);
            if (val != null && !val.isBlank()) {
                try {
                    return LocalDateTime.parse(val, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));
                } catch (DateTimeParseException ignored) {}
            }
        }
        return null;
    }

    private String parseFocalLength(ExifSubIFDDirectory dir) {
        try {
            var desc = dir.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
            if (desc != null) return desc;
        } catch (Exception ignored) {}
        return null;
    }

    private String parseAperture(ExifSubIFDDirectory dir) {
        try {
            var desc = dir.getDescription(ExifSubIFDDirectory.TAG_FNUMBER);
            if (desc != null) return desc;
        } catch (Exception ignored) {}
        return null;
    }

    private String parseShutterSpeed(ExifSubIFDDirectory dir) {
        try {
            var desc = dir.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
            if (desc != null) return desc;
        } catch (Exception ignored) {}
        return null;
    }

    private Integer parseIso(ExifSubIFDDirectory dir) {
        try {
            String val = dir.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
            if (val != null && !val.isBlank()) return Integer.parseInt(val);
        } catch (Exception ignored) {}
        return null;
    }

    public int getOrientation(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null && ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private boolean hasAnyData(ExifData e) {
        return e.getDateTaken() != null || e.getCameraModel() != null
            || e.getLensModel() != null || e.getFocalLength() != null
            || e.getAperture() != null || e.getShutterSpeed() != null
            || e.getIso() != null || e.getLatitude() != null
            || e.getLongitude() != null;
    }
}
