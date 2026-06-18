package com.example.demo;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoService {

    private final PhotoRepository repo;
    private final TagRepository tagRepo;
    private final CategoryRepository catRepo;
    private final Path uploadDir;

    public PhotoService(PhotoRepository repo, TagRepository tagRepo, CategoryRepository catRepo,
                        @Value("${photo.upload-dir:uploads}") String uploadDir) {
        this.repo = repo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

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

    public Photo getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("该照片已被删除或不存在"));
    }

    public Page<Photo> findByIds(List<Long> ids, Pageable pageable) {
        return repo.findByIdIn(ids, pageable);
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int THUMBNAIL_WIDTH = 400;

    public Photo upload(MultipartFile file, String name, String description,
                        List<Long> tagIds, Long categoryId) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        validateImageMagicBytes(file.getInputStream());

        LocalDateTime now = LocalDateTime.now();
        String dateDir = String.format("%04d/%02d", now.getYear(), now.getMonthValue());
        Path datePath = uploadDir.resolve(dateDir);
        Files.createDirectories(datePath);

        String baseName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String storedName = dateDir + "/" + baseName;
        Path target = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        generateThumbnail(target, dateDir, baseName);

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

        return repo.save(photo);
    }

    @Transactional
    public Photo update(Long id, String name, String description,
                        List<Long> tagIds, Long categoryId) {
        Photo photo = getById(id);
        if (name != null && !name.isBlank()) photo.setName(name);
        if (description != null) photo.setDescription(description);
        if (tagIds != null) photo.setTags(new HashSet<>(tagRepo.findAllById(tagIds)));
        photo.setCategory(categoryId != null ? catRepo.findById(categoryId).orElse(null) : null);
        return repo.save(photo);
    }

    // === 标签 ===
    public List<Tag> listTags() { return tagRepo.findAll(); }
    public Tag createTag(String name, String color) {
        return tagRepo.findByName(name).orElseGet(() -> tagRepo.save(new Tag(name, color)));
    }
    public void deleteTag(Long id) { tagRepo.deleteById(id); }
    public Tag updateTag(Long id, String name, String color) {
        Tag tag = tagRepo.findById(id).orElseThrow(() -> new RuntimeException("标签不存在"));
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        return tagRepo.save(tag);
    }

    // === 分类 ===
    public List<Category> listCategories() { return catRepo.findAll(); }
    public Category createCategory(String name) {
        return catRepo.findByName(name).orElseGet(() -> catRepo.save(new Category(name)));
    }
    public void deleteCategory(Long id) { catRepo.deleteById(id); }
    public Category updateCategory(Long id, String name) {
        Category cat = catRepo.findById(id).orElseThrow(() -> new RuntimeException("分类不存在"));
        if (name != null) cat.setName(name);
        return catRepo.save(cat);
    }

    // === 原有方法保持不变 ===

    private void generateThumbnail(Path original, String dateDir, String baseName) throws IOException {
        BufferedImage image = ImageIO.read(original.toFile());
        if (image == null) return;

        int h = (int) ((double) image.getHeight() / image.getWidth() * THUMBNAIL_WIDTH);
        BufferedImage thumb = new BufferedImage(THUMBNAIL_WIDTH, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, THUMBNAIL_WIDTH, h, null);
        g.dispose();

        Path thumbDir = uploadDir.resolve(dateDir).resolve("thumbnails");
        Files.createDirectories(thumbDir);
        Path thumbPath = thumbDir.resolve(baseName);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);
            writer.setOutput(new FileImageOutputStream(thumbPath.toFile()));
            writer.write(null, new IIOImage(thumb, null, null), param);
            writer.dispose();
        } else {
            ImageIO.write(thumb, "jpeg", thumbPath.toFile());
        }
    }

    public Path getFilePath(Long id) {
        Photo photo = getById(id);
        return uploadDir.resolve(photo.getFileName());
    }

    public Path getThumbnailPath(Long id) {
        Photo photo = getById(id);
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        Path thumb = uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName);
        if (Files.exists(thumb)) return thumb;
        return uploadDir.resolve(fn);
    }

    public void delete(Long id) {
        Photo photo = getById(id);
        try {
            Files.deleteIfExists(uploadDir.resolve(photo.getFileName()));
            String fn = photo.getFileName();
            int lastSlash = fn.lastIndexOf('/');
            String dateDir = fn.substring(0, lastSlash);
            String baseName = fn.substring(lastSlash + 1);
            Files.deleteIfExists(uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName));
        } catch (IOException ignored) {}
        repo.delete(photo);
    }

    public int batchDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try {
                Photo photo = repo.findById(id).orElse(null);
                if (photo == null) continue;
                Files.deleteIfExists(uploadDir.resolve(photo.getFileName()));
                String fn = photo.getFileName();
                int lastSlash = fn.lastIndexOf('/');
                String dateDir = fn.substring(0, lastSlash);
                String baseName = fn.substring(lastSlash + 1);
                Files.deleteIfExists(uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName));
                repo.delete(photo);
                count++;
            } catch (IOException ignored) {}
        }
        return count;
    }

    public List<Photo> batchUpload(List<MultipartFile> files, String name, String description,
                                    List<Long> tagIds, Long categoryId) throws IOException {
        List<Photo> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            results.add(upload(file, name, description, tagIds, categoryId));
        }
        return results;
    }

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
                generateThumbnail(original, dateDir, baseName);
                if (Files.exists(thumb)) count++;
            } catch (IOException ignored) {}
        }
        return count;
    }

    private void validateImageMagicBytes(InputStream in) throws IOException {
        byte[] header = new byte[12];
        int read = in.read(header);
        if (read <= 0) throw new InvalidFileTypeException("无法识别文件内容，请上传图片文件");
        if (read >= 2 && header[0] == (byte)0xFF && header[1] == (byte)0xD8) return;
        if (read >= 4 && header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) return;
        if (read >= 4 && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) return;
        if (read >= 2 && header[0] == 0x42 && header[1] == 0x4D) return;
        if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) return;
        throw new InvalidFileTypeException("文件格式不支持，请上传常见的图片文件");
    }
}
