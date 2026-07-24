package com.project.FitLink.controller;

import com.project.FitLink.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "Home Page")
public class Home {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of("message", "Welcome to the Home Page!"));
    }

    // Test controller and will be deleted

}
