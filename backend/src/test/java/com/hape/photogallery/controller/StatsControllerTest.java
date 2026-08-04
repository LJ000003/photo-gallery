package com.hape.photogallery.controller;

import com.hape.photogallery.dto.StatsResponse;
import com.hape.photogallery.dto.StatsResponse.MonthlyTrend;
import com.hape.photogallery.dto.StatsResponse.TopTag;
import com.hape.photogallery.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = StatsController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import({com.hape.photogallery.config.JwtService.class, com.hape.photogallery.config.ClientIpResolver.class, com.hape.photogallery.config.MediaSignatureService.class})
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsService statsService;

    @Test
    void getStats_shouldReturnAggregatedData() throws Exception {
        StatsResponse response = new StatsResponse(
                128,
                123_456_789L,
                List.of(new MonthlyTrend("2026-08", 12)),
                List.of(new TopTag("旅行", "#ff8800", 24)));

        when(statsService.getStats()).thenReturn(response);

        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalPhotos").value(128))
                .andExpect(jsonPath("$.data.totalSize").value(123_456_789))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-08"))
                .andExpect(jsonPath("$.data.monthlyTrend[0].count").value(12))
                .andExpect(jsonPath("$.data.topTags[0].name").value("旅行"))
                .andExpect(jsonPath("$.data.topTags[0].color").value("#ff8800"))
                .andExpect(jsonPath("$.data.topTags[0].count").value(24));
    }
}
