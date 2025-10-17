package com.apontaja.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protected")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> protectedEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a protected endpoint - you are authenticated!");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is an admin endpoint");
        return ResponseEntity.ok(response);
    }
}
