package com.project.FitLink.controller.test;

import com.project.FitLink.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
@Tag(name = "Protected Controller", description = "Test for Tokens")
public class TestController {
    @GetMapping
    public ResponseEntity<Map<String, Object>> test() {
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", "response from protected controller");
        return ResponseEntity.ok(response.getApiResponse());
    }

    // this for test protected apis and will be deleted
}
