package com.project.FitLink.controller.home;

import com.project.FitLink.dto.GlobalResponse;
import com.project.FitLink.service.auth.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", "Welcome to the Home Page!");
        return ResponseEntity.ok(response.getApiResponse());
    }

    // Test controller and will be deleted

}
