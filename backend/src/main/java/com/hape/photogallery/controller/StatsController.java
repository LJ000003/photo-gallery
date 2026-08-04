package com.hape.photogallery.controller;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.StatsResponse;
import com.hape.photogallery.service.StatsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public ApiResponse<StatsResponse> getStats() {
        return ApiResponse.success(statsService.getStats());
    }
}
