package com.hape.photogallery.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.util.CoordUtil;

/**
 * 照片查询侧服务（P2-#15 从 PhotoService 拆出）：列表/搜索/详情/时间线/地图/DTO 转换。
 * 写路径（PhotoService）单向依赖本服务（getById/toResponse），避免互相注入的循环依赖；
 * 转换方法（toResponse/toTimelineItem/toMapItem）随本服务走，@Cacheable key 与拆出前一致。
 */
@Service
public class PhotoQueryService {

    private final PhotoRepository repo;
    private final ExifDataRepository exifRepo;
    private final ExifService exifService;
    private final MediaSignatureService mediaSignature;
    private final StorageService storage;
    private final FullTextProbe fullTextProbe;

    public PhotoQueryService(PhotoRepository repo, ExifDataRepository exifRepo,
                             ExifService exifService,
                             MediaSignatureService mediaSignature,
                             StorageService storage,
                             FullTextProbe fullTextProbe) {
        this.repo = repo;
        this.exifRepo = exifRepo;
        this.exifService = exifService;
        this.mediaSignature = mediaSignature;
        this.storage = storage;
        this.fullTextProbe = fullTextProbe;
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

    /** 缓存照片列表（DTO 形式，避免 Hibernate 懒加载代理被序列化到 Redis） */
    @Transactional(readOnly = true)
    @Cacheable(value = "photos", key = "{#tagIds, #categoryIds, #pageable}")
    public Page<PhotoResponse> listAllResponses(List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        return listAll(tagIds, categoryIds, pageable).map(this::toResponse);
    }

    /**
     * 搜索（含标签/分类组合过滤）。
     * native query 的排序必须是数据库列名（Hibernate 不做属性→列名翻译），
     * 而前端传的是实体属性名（createdAt/fileSize），需在此映射，否则 MySQL 报 Unknown column。
     */
    public Page<Photo> search(String q, List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        if (q == null || q.isBlank()) return repo.findAll(pageable);
        Pageable columnSort = toColumnSort(pageable);
        // 先剥离 FULLTEXT BOOLEAN MODE 运算符再判长度：`ab"` → `ab`，`a"` → `a`（应走 LIKE）
        String query = sanitizeFullText(q);
        if (query.isEmpty()) return Page.empty(pageable);
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasCats = categoryIds != null && !categoryIds.isEmpty();
        String pattern = "%" + escapeLike(query) + "%";
        // 单字（<2 字符）：FULLTEXT 的 ngram 双字分词无法命中，fallback 到 LIKE 子串匹配；
        // 非 MySQL 数据库（H2 等测试环境）不支持 MATCH...AGAINST（语法错误 → 500），任意长度一律 LIKE
        if (query.length() < 2 || !fullTextProbe.isSupported()) {
            if (hasTags && hasCats) return repo.searchByLikeWithTagAndCategoryIds(pattern, tagIds, categoryIds, columnSort);
            if (hasTags) return repo.searchByLikeWithTagIds(pattern, tagIds, columnSort);
            if (hasCats) return repo.searchByLikeWithCategoryIds(pattern, categoryIds, columnSort);
            return repo.searchByLike(pattern, columnSort);
        }
        if (hasTags && hasCats) return repo.searchWithTagAndCategoryIds(query, tagIds, categoryIds, columnSort);
        if (hasTags) return repo.searchWithTagIds(query, tagIds, columnSort);
        if (hasCats) return repo.searchWithCategoryIds(query, categoryIds, columnSort);
        return repo.search(query, columnSort);
    }

    /** LIKE 通配符转义（MySQL 默认转义字符为反斜杠） */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** FULLTEXT BOOLEAN MODE 运算符：MySQL 将其按查询表达式解析（`-x` 排除、`"` 短语），
     *  参数绑定防不了语法错误（`ab"` → 1210 → 500）与语义劫持，只能从输入中剥离 */
    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("[+\\-<>()~*\"@]");

    /** 剥离 BOOLEAN MODE 运算符并折叠空白：用户输入一律按纯关键词处理 */
    static String sanitizeFullText(String q) {
        String cleaned = BOOLEAN_OPERATORS.matcher(q).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /** 实体属性名 → 数据库列名（native query 排序用）。
     *  白名单之外的属性一律 400，杜绝 ORDER BY 字符串拼接注入（前端仅用 createdAt/name/fileSize） */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "fileSize", "file_size",
            "name", "name"
    );

    private Pageable toColumnSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) return pageable;
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new BusinessException(400, "不支持的排序字段: " + order.getProperty());
            }
            orders.add(new Sort.Order(order.getDirection(), column));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    @Transactional(readOnly = true)
    public Photo getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "该照片已被删除或不存在"));
    }

    @Transactional(readOnly = true)
    public PhotoResponse getPhotoResponse(Long id) {
        return toResponse(getById(id));
    }

    public Page<Photo> findByIds(List<Long> ids, Pageable pageable) {
        return repo.findByIdIn(ids, pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "timeline", key = "{#sortOrder, #pageable}")
    public Page<TimelineItem> getTimeline(String sortOrder, Pageable pageable) {
        Page<ExifData> page = "asc".equalsIgnoreCase(sortOrder)
                ? exifRepo.findWithDateTakenAndPhotoAsc(pageable)
                : exifRepo.findWithDateTakenAndPhotoDesc(pageable);
        return page.map(this::toTimelineItem);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "map", key = "{#swLat, #swLng, #neLat, #neLng}")
    public List<MapItem> getMapPhotos(double swLat, double swLng, double neLat, double neLng) {
        List<ExifData> list = exifRepo.findWithGpsInBounds(swLat, swLng, neLat, neLng,
                PageRequest.of(0, 500));
        // 必须走 toMapItem（内联 MapItem.from 会漏掉 mediaToken 短时签名，
        // 前端 popup 缩略图无鉴权 401）
        return list.stream().map(e -> {
            MapItem item = toMapItem(e);
            double[] gcj = CoordUtil.wgs84ToGcj02(e.getLongitude(), e.getLatitude());
            item.setLatitude(gcj[1]);
            item.setLongitude(gcj[0]);
            return item;
        }).toList();
    }

    // extractExifForExisting（存量 EXIF 批量提取）已随迁移方法拆至 MigrationService（P4-#37）

    public ExifData extractExifForPhoto(Long id) {
        Photo photo = getById(id);
        Path filePath = storage.getUploadDir().resolve(photo.getFileName());
        if (!Files.exists(filePath)) return null;
        return exifService.extractAndSave(photo, filePath);
    }

    // === DTO 转换 ===

    public PhotoResponse toResponse(Photo photo) {
        PhotoResponse r = PhotoResponse.from(photo);
        r.setMediaToken(mediaSignature.sign(photo.getId()));
        return r;
    }

    public TimelineItem toTimelineItem(ExifData exif) {
        TimelineItem item = TimelineItem.from(exif);
        item.setMediaToken(mediaSignature.sign(exif.getPhotoId()));
        return item;
    }

    public MapItem toMapItem(ExifData exif) {
        MapItem item = MapItem.from(exif);
        item.setMediaToken(mediaSignature.sign(exif.getPhotoId()));
        return item;
    }
}
