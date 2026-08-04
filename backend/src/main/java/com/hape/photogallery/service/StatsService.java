package com.hape.photogallery.service;

import java.util.List;

import com.hape.photogallery.dto.StatsResponse;
import com.hape.photogallery.dto.StatsResponse.MonthlyTrend;
import com.hape.photogallery.dto.StatsResponse.TopTag;
import com.hape.photogallery.repository.PhotoRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private final PhotoRepository photoRepo;

    public StatsService(PhotoRepository photoRepo) {
        this.photoRepo = photoRepo;
    }

    /**
     * 统计面板聚合数据。写操作（PhotoService/TagService 的 @CacheEvict 清单）主动失效，
     * 30s TTL 作为兜底。
     */
    @Cacheable(value = "stats", key = "'summary'")
    public StatsResponse getStats() {
        List<Object[]> monthRows = photoRepo.countGroupedByMonth();
        List<Object[]> tagRows = photoRepo.countByTag(Pageable.ofSize(10));

        List<MonthlyTrend> trend = monthRows.stream()
                .map(row -> new MonthlyTrend(
                        String.format("%04d-%02d",
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).intValue()),
                        ((Number) row[2]).longValue()))
                .toList();

        List<TopTag> topTags = tagRows.stream()
                .map(row -> new TopTag(
                        (String) row[0],
                        row[1] != null ? (String) row[1] : null,
                        ((Number) row[2]).longValue()))
                .toList();

        return new StatsResponse(photoRepo.countAll(), photoRepo.sumFileSize(), trend, topTags);
    }
}
