package com.hape.photogallery.controller;

import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TagController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagService tagService;

    @Test
    void listTags_shouldReturnTags() throws Exception {
        when(tagService.listAll()).thenReturn(List.of(new Tag("日出", "#ff8800")));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createTag_shouldReturnTag() throws Exception {
        Tag tag = new Tag("新标签", "#ff0"); tag.setId(1L);
        when(tagService.create("新标签", "#ff0")).thenReturn(tag);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新标签\",\"color\":\"#ff0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新标签"));
    }
}
