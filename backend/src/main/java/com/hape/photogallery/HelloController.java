package com.hape.photogallery;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public MessageResponse hello() {
        return new MessageResponse("Hello, World!", LocalDateTime.now().toString());
    }

    public record MessageResponse(String message, String time) {
    }
}