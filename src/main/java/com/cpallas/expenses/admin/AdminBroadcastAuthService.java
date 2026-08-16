package com.cpallas.expenses.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminBroadcastAuthService {

    private final byte[] configuredToken;

    public AdminBroadcastAuthService(
            @Value("${expense.admin.broadcast-token:}") String configuredToken
    ) {
        this.configuredToken = configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    public void requireAuthorized(String providedToken) {
        if (configuredToken.length == 0 || providedToken == null) {
            throw new AdminBroadcastUnauthorizedException();
        }
        byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(configuredToken, provided)) {
            throw new AdminBroadcastUnauthorizedException();
        }
    }
}
