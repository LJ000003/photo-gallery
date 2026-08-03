package com.hape.photogallery.controller;

import com.hape.photogallery.dto.BackupExportRequest;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.service.BackupService;
import com.hape.photogallery.service.BackupService.BackupBundle;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = BackupController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import({com.hape.photogallery.config.JwtService.class, com.hape.photogallery.config.ClientIpResolver.class})
class BackupControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private BackupService backupService;

    private BackupBundle anyBundle() {
        return new BackupBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                LocalDateTime.of(2026, 8, 2, 12, 0), null);
    }

    @Test
    void export_shouldReturnStreamingResponseWithHeaders() throws Exception {
        when(backupService.collect(any())).thenReturn(anyBundle());

        MvcResult result = mockMvc.perform(post("/api/v1/backup/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("photo-gallery-backup-")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".tar.gz")))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void export_withoutBody_shouldUseEmptyRequest() throws Exception {
        when(backupService.collect(any())).thenReturn(anyBundle());

        MvcResult result = mockMvc.perform(post("/api/v1/backup/export"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        ArgumentCaptor<BackupExportRequest> captor = ArgumentCaptor.forClass(BackupExportRequest.class);
        verify(backupService).collect(captor.capture());
        assertThat(captor.getValue().getAlbumId()).isNull();
        assertThat(captor.getValue().getDateFrom()).isNull();
    }

    @Test
    void export_withFilters_shouldPassToService() throws Exception {
        when(backupService.collect(any())).thenReturn(anyBundle());

        MvcResult result = mockMvc.perform(post("/api/v1/backup/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"albumId\":3,\"categoryId\":5,\"dateFrom\":\"2026-01-01\",\"dateTo\":\"2026-07-31\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        ArgumentCaptor<BackupExportRequest> captor = ArgumentCaptor.forClass(BackupExportRequest.class);
        verify(backupService).collect(captor.capture());
        BackupExportRequest req = captor.getValue();
        assertThat(req.getAlbumId()).isEqualTo(3L);
        assertThat(req.getCategoryId()).isEqualTo(5L);
        assertThat(req.getDateFrom()).isEqualTo("2026-01-01");
        assertThat(req.getDateTo()).isEqualTo("2026-07-31");
    }

    @Test
    void export_emptyResult_shouldReturn400Json() throws Exception {
        doThrow(new BusinessException(400, "没有符合条件的照片"))
                .when(backupService).collect(any());

        mockMvc.perform(post("/api/v1/backup/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("没有符合条件的照片"));
    }
}
