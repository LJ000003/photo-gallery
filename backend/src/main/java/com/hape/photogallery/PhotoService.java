package com.hape.photogallery;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final PhotoRepository repo;
    private final TagRepository tagRepo;
    private final CategoryRepository catRepo;
    private final ExifDataRepository exifRepo;
    private final ExifService exifService;
    private final ImageProcessingService imageService;
    private final AlbumService albumService;
    private final Path uploadDir;

    public PhotoService(PhotoRepository repo, TagRepository tagRepo, CategoryRepository catRepo,
                        ExifDataRepository exifRepo,
                        ExifService exifService,
                        ImageProcessingService imageService,
                        AlbumService albumService,
                        @Value("${photo.upload-dir:uploads}") String uploadDir) {
        this.repo = repo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.exifRepo = exifRepo;
        this.exifService = exifService;
        this.imageService = imageService;
        this.albumService = albumService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    @Cacheable(value = "photos", key = "{#tagIds, #categoryIds, #pageable}")
    public Page<Photo> listAll(List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasCats = categoryIds != null && !categoryIds.isEmpty();
        if (hasTags && hasCats) {
            return repo.findByCategoryIdsAndTagIds(categoryIds, tagIds, pageable);
        } else if (hasTags) {
            return repo.findByTagIds(tagIds, pageable);
        } else if (hasCats) {
            return repo.findByCategoryIds(categoryIds, pageable);
        }
        return repo.findAll(pageable);
    }

    public Page<Photo> search(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return repo.findAll(pageable);
        return repo.search(q.trim(), pageable);
    }

    public Photo getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "该照片已被删除或不存在"));
    }

    public Page<Photo> findByIds(List<Long> ids, Pageable pageable) {
        return repo.findByIdIn(ids, pageable);
    }

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public Photo upload(MultipartFile file, String name, String description,
                        List<Long> tagIds, Long categoryId, String watermark) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        imageService.validateImageMagicBytes(file.getInputStream());

        LocalDateTime now = LocalDateTime.now();
        String dateDir = String.format("%04d/%02d", now.getYear(), now.getMonthValue());
        Path datePath = uploadDir.resolve(dateDir);
        Files.createDirectories(datePath);

        String baseName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String storedName = dateDir + "/" + baseName;
        Path target = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        // 先存 Photo 拿到 ID，然后在图片处理之前提取 EXIF（否则重编码会丢失 EXIF）
        Photo photo = new Photo();
        photo.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        photo.setDescription(description);
        photo.setFileName(storedName);
        photo.setOriginalFileName(file.getOriginalFilename());
        photo.setFileSize(file.getSize());
        photo.setContentType(file.getContentType());
        photo.setCreatedAt(now);

        if (tagIds != null && !tagIds.isEmpty()) {
            photo.setTags(new HashSet<>(tagRepo.findAllById(tagIds)));
        }
        if (categoryId != null) {
            photo.setCategory(catRepo.findById(categoryId).orElse(null));
        }

        Photo saved = repo.save(photo);

        // 图片处理之前提取 EXIF，保留原始元数据
        try {
            exifService.extractAndSave(saved, target);
        } catch (Exception e) {
            // EXIF extraction failure should not block upload
        }

        imageService.autoRotateIfNeeded(target);

        if (watermark != null && !watermark.isBlank()) {
            imageService.applyWatermark(target, watermark);
        }

        imageService.generateThumbnail(target, dateDir, baseName);
        imageService.generateThumbnail(target, dateDir, baseName, 200);
        imageService.generateWebp(target, dateDir, baseName);

        return saved;
    }

    @CacheEvict(value = "photos", allEntries = true)
    @Transactional
    public PhotoResponse update(Long id, PhotoUpdateRequest req) {
        Photo photo = getById(id);
        photo.setName(req.getName());
        photo.setDescription(req.getDescription());
        if (req.getTagIds() != null) {
            photo.setTags(new HashSet<>(tagRepo.findAllById(req.getTagIds())));
        }
        photo.setCategory(req.getCategoryId() != null
                ? catRepo.findById(req.getCategoryId()).orElse(null) : null);
        if (req.getAlbumIds() != null) {
            albumService.syncPhotoAlbums(photo, req.getAlbumIds());
        }
        return toResponse(repo.save(photo));
    }

    // === 文件路径 ===

    /** 安全解析上传目录内的相对路径，防止 ../ 穿越 */
    private Path resolveSafe(String relativePath) {
        Path resolved = uploadDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }
        return resolved;
    }

    public Path getFilePath(Long id) {
        Photo photo = getById(id);
        return resolveSafe(photo.getFileName());
    }

    public Path getThumbnailPath(Long id) {
        return getThumbnailPath(id, 400);
    }

    public Path getThumbnailPath(Long id, int width) {
        Photo photo = getById(id);
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);

        Path thumbDir = width == 400
                ? uploadDir.resolve(dateDir).resolve("thumbnails")
                : uploadDir.resolve(dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Path thumb = resolveSafe(uploadDir.relativize(thumbDir.resolve(baseName)).toString());
        if (Files.exists(thumb)) return thumb;

        // 小尺寸不存在时，回退到 400px 档
        if (width != 400) {
            Path fallback = resolveSafe(dateDir + "/thumbnails/" + baseName);
            if (Files.exists(fallback)) return fallback;
        }

        return resolveSafe(fn);
    }

    public Path getWebpPath(Long id) {
        Photo photo = getById(id);
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        Path webp = resolveSafe(dateDir + "/webp/" + baseName + ".webp");
        if (Files.exists(webp)) return webp;
        return getFilePath(id);
    }

    // === 删除 ===

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public void delete(Long id) {
        Photo photo = getById(id);
        exifRepo.findByPhoto_Id(id).ifPresent(exifRepo::delete);
        repo.delete(photo);
        deletePhotoFiles(photo);
    }

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public int batchDelete(List<Long> ids) {
        List<Photo> photos = repo.findAllById(ids);
        if (photos.isEmpty()) return 0;
        List<Long> photoIds = photos.stream().map(Photo::getId).toList();
        exifRepo.deleteByPhoto_IdIn(photoIds);
        repo.deleteAll(photos);
        for (Photo photo : photos) {
            deletePhotoFiles(photo);
        }
        return photos.size();
    }

    private void deletePhotoFiles(Photo photo) {
        try {
            Files.deleteIfExists(uploadDir.resolve(photo.getFileName()));
            String fn = photo.getFileName();
            int lastSlash = fn.lastIndexOf('/');
            String dateDir = fn.substring(0, lastSlash);
            String baseName = fn.substring(lastSlash + 1);
            Files.deleteIfExists(uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName));
            Files.deleteIfExists(uploadDir.resolve(dateDir).resolve("thumbnails").resolve("200").resolve(baseName));
            Files.deleteIfExists(uploadDir.resolve(dateDir).resolve("webp").resolve(baseName + ".webp"));
        } catch (IOException ignored) {}
    }

    // === 批量上传 ===

    @CacheEvict(value = "photos", allEntries = true)
    public List<Photo> batchUpload(List<MultipartFile> files, String name, String description,
                                    List<Long> tagIds, Long categoryId, String watermark) throws IOException {
        List<Photo> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            results.add(upload(file, name, description, tagIds, categoryId, watermark));
        }
        return results;
    }

    // === 迁移 ===

    public int migrateThumbnails() {
        List<Photo> all = repo.findAll();
        int count = 0;
        for (Photo p : all) {
            String fn = p.getFileName();
            int lastSlash = fn.lastIndexOf('/');
            String dateDir = fn.substring(0, lastSlash);
            String baseName = fn.substring(lastSlash + 1);
            Path thumb = uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName);
            if (Files.exists(thumb)) continue;
            Path original = uploadDir.resolve(fn);
            if (!Files.exists(original)) continue;
            try {
                imageService.generateThumbnail(original, dateDir, baseName);
                if (Files.exists(thumb)) count++;
            } catch (IOException ignored) {}
        }
        return count;
    }

    public int migrateWebp() {
        List<Photo> all = repo.findAll();
        int count = 0;
        for (Photo p : all) {
            String fn = p.getFileName();
            int lastSlash = fn.lastIndexOf('/');
            String dateDir = fn.substring(0, lastSlash);
            String baseName = fn.substring(lastSlash + 1);
            Path webp = uploadDir.resolve(dateDir).resolve("webp").resolve(baseName + ".webp");
            if (Files.exists(webp)) continue;
            Path original = uploadDir.resolve(fn);
            if (!Files.exists(original)) continue;
            imageService.generateWebp(original, dateDir, baseName);
            if (Files.exists(webp)) count++;
        }
        return count;
    }

    // === EXIF ===

    public List<TimelineItem> getTimeline(String sortOrder) {
        List<ExifData> list = "asc".equalsIgnoreCase(sortOrder)
                ? exifRepo.findWithDateTakenAndPhotoAsc()
                : exifRepo.findWithDateTakenAndPhotoDesc();
        return list.stream().map(this::toTimelineItem).toList();
    }

    public List<MapItem> getMapPhotos() {
        List<ExifData> list = exifRepo.findWithGpsAndPhoto();
        for (ExifData e : list) {
            double[] gcj = CoordUtil.wgs84ToGcj02(e.getLongitude(), e.getLatitude());
            e.setLongitude(gcj[0]);
            e.setLatitude(gcj[1]);
        }
        return list.stream().map(this::toMapItem).toList();
    }

    public int extractExifForExisting() {
        var photos = repo.findAll();
        return exifService.extractForExisting(photos, uploadDir);
    }

    public ExifData extractExifForPhoto(Long id) {
        Photo photo = getById(id);
        Path filePath = uploadDir.resolve(photo.getFileName());
        if (!Files.exists(filePath)) return null;
        return exifService.extractAndSave(photo, filePath);
    }

    // === 变换 ===

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public void transformPhoto(Long id, int rotate, String mirror, Double cx, Double cy, Double cw, Double ch) throws IOException {
        Photo photo = getById(id);
        Path filePath = uploadDir.resolve(photo.getFileName());
        if (!Files.exists(filePath)) return;

        BufferedImage img = ImageIO.read(filePath.toFile());
        if (img == null) throw new IOException("无法读取图片");

        if (cx != null && cy != null && cw != null && ch != null
                && cw > 0 && ch > 0 && cw < 1 && ch < 1) {
            int x = (int) (img.getWidth() * cx);
            int y = (int) (img.getHeight() * cy);
            int w = (int) (img.getWidth() * cw);
            int h = (int) (img.getHeight() * ch);
            x = Math.max(0, Math.min(x, img.getWidth() - 1));
            y = Math.max(0, Math.min(y, img.getHeight() - 1));
            w = Math.max(1, Math.min(w, img.getWidth() - x));
            h = Math.max(1, Math.min(h, img.getHeight() - y));
            img = img.getSubimage(x, y, w, h);
        }

        if (rotate > 0) {
            img = imageService.rotateImage(img, rotate % 360);
        }

        if ("horizontal".equals(mirror)) {
            img = imageService.mirrorImage(img, true);
        } else if ("vertical".equals(mirror)) {
            img = imageService.mirrorImage(img, false);
        }

        String format = imageService.getFormat(filePath);
        ImageIO.write(img, format, filePath.toFile());
        photo.setFileSize(Files.size(filePath));

        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        imageService.generateThumbnail(filePath, dateDir, baseName);
        imageService.generateThumbnail(filePath, dateDir, baseName, 200);
        imageService.generateWebp(filePath, dateDir, baseName);
        repo.save(photo);

        try {
            exifService.extractAndSave(photo, filePath);
        } catch (Exception ignored) {}
    }

    // === DTO 转换 ===

    public PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.from(photo);
    }

    public TimelineItem toTimelineItem(ExifData exif) {
        return TimelineItem.from(exif);
    }

    public MapItem toMapItem(ExifData exif) {
        return MapItem.from(exif);
    }
}
