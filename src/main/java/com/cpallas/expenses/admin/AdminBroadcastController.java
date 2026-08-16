package com.cpallas.expenses.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminBroadcastAuthService authService;
    private final AdminBroadcastService broadcastService;

    @PostMapping("/broadcast")
    public AdminBroadcastDtos.Result broadcast(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody AdminBroadcastDtos.Request request
    ) {
        authService.requireAuthorized(adminToken);
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        return broadcastService.broadcast(request.text());
    }

    @ExceptionHandler(AdminBroadcastUnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(AdminBroadcastUnauthorizedException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
