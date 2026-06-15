package com.example.demo;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Photo> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public Photo get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public Photo upload(@RequestParam("file") MultipartFile file,
                        @RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "description", required = false) String description)
            throws IOException {
        return service.upload(file, name, description);
    }

    @PutMapping("/{id}")
    public Photo update(@PathVariable Long id, @Valid @RequestBody Photo body) {
        return service.update(id, body.getName(), body.getDescription());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable Long id) {
        Photo photo = service.getById(id);
        Resource resource = new FileSystemResource(service.getFilePath(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .body(resource);
    }

}
