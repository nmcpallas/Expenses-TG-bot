package com.cpallas.expenses.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBroadcastAuthServiceTest {

    @Test
    void acceptsConfiguredToken() {
        AdminBroadcastAuthService service = new AdminBroadcastAuthService("secret-token");

        assertThatCode(() -> service.requireAuthorized("secret-token"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingWrongAndUnconfiguredTokens() {
        AdminBroadcastAuthService service = new AdminBroadcastAuthService("secret-token");

        assertThatThrownBy(() -> service.requireAuthorized(null))
                .isInstanceOf(AdminBroadcastUnauthorizedException.class);
        assertThatThrownBy(() -> service.requireAuthorized("wrong-token"))
                .isInstanceOf(AdminBroadcastUnauthorizedException.class);
        assertThatThrownBy(() -> new AdminBroadcastAuthService("").requireAuthorized(""))
                .isInstanceOf(AdminBroadcastUnauthorizedException.class);
    }
}
