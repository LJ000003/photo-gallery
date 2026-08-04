package com.hape.photogallery.service;

import com.hape.photogallery.dto.StatsResponse;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private PhotoRepository photoRepo;

    private StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(photoRepo);
    }

    @Test
    void getStats_shouldAggregateAllDimensions() {
        when(photoRepo.countAll()).thenReturn(128L);
        when(photoRepo.sumFileSize()).thenReturn(123_456_789L);
        when(photoRepo.countGroupedByMonth()).thenReturn(List.of(
                new Object[]{2026, 7, 5L},
                new Object[]{2026, 8, 12L}));
        when(photoRepo.countByTag(any(Pageable.class))).thenReturn(List.of(
                new Object[]{"旅行", "#ff8800", 24L},
                new Object[]{"美食", null, 7L}));

        StatsResponse stats = service.getStats();

        assertThat(stats.getTotalPhotos()).isEqualTo(128);
        assertThat(stats.getTotalSize()).isEqualTo(123_456_789L);
        assertThat(stats.getMonthlyTrend()).hasSize(2);
        assertThat(stats.getMonthlyTrend().get(0).getMonth()).isEqualTo("2026-07");
        assertThat(stats.getMonthlyTrend().get(0).getCount()).isEqualTo(5);
        assertThat(stats.getMonthlyTrend().get(1).getMonth()).isEqualTo("2026-08");
        assertThat(stats.getMonthlyTrend().get(1).getCount()).isEqualTo(12);
        assertThat(stats.getTopTags()).hasSize(2);
        assertThat(stats.getTopTags().get(0).getName()).isEqualTo("旅行");
        assertThat(stats.getTopTags().get(0).getColor()).isEqualTo("#ff8800");
        assertThat(stats.getTopTags().get(0).getCount()).isEqualTo(24);
        // 无颜色标签不 NPE
        assertThat(stats.getTopTags().get(1).getColor()).isNull();
    }

    @Test
    void getStats_emptyLibrary_shouldReturnZerosAndEmptyLists() {
        when(photoRepo.countAll()).thenReturn(0L);
        when(photoRepo.sumFileSize()).thenReturn(0L);
        when(photoRepo.countGroupedByMonth()).thenReturn(List.of());
        when(photoRepo.countByTag(any(Pageable.class))).thenReturn(List.of());

        StatsResponse stats = service.getStats();

        assertThat(stats.getTotalPhotos()).isZero();
        assertThat(stats.getTotalSize()).isZero();
        assertThat(stats.getMonthlyTrend()).isEmpty();
        assertThat(stats.getTopTags()).isEmpty();
    }
}
