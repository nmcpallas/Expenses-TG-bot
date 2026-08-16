package com.cpallas.expenses.miniapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cpallas.expenses.storage.ids.ChatId;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramMiniAppAuthServiceTest {

    private static final String TOKEN = "123456:test-token";
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private final TelegramMiniAppAuthService service = new TelegramMiniAppAuthService(
            TOKEN,
            Duration.ofHours(1),
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void authenticatesSignedTelegramInitData() {
        String authorization = authorization(NOW.minusSeconds(30), 42L, "Наиль");

        TelegramMiniAppPrincipal principal = service.authenticate(authorization);

        assertThat(principal.userId().getId()).isEqualTo(42L);
        assertThat(principal.chatId().getId()).isEqualTo(42L);
        assertThat(principal.firstName()).isEqualTo("Наиль");
    }

    @Test
    void authenticatesCurrentTelegramInitDataWithThirdPartySignature() {
        String authorization = authorization(
                NOW.minusSeconds(30),
                42L,
                "Наиль",
                Map.of("signature", "telegram-ed25519-signature")
        );

        TelegramMiniAppPrincipal principal = service.authenticate(authorization);

        assertThat(principal.userId().getId()).isEqualTo(42L);
        assertThat(principal.chatId().getId()).isEqualTo(42L);
    }

    @Test
    void authenticatesSignedGroupBudgetContext() {
        String startParam = new MiniAppLaunchContextService(TOKEN)
                .createGroupStartParam(new ChatId(-100123L));
        String authorization = authorization(
                NOW.minusSeconds(30),
                42L,
                "Наиль",
                Map.of("start_param", startParam)
        );

        TelegramMiniAppPrincipal principal = service.authenticate(authorization);

        assertThat(principal.userId().getId()).isEqualTo(42L);
        assertThat(principal.chatId().getId()).isEqualTo(-100123L);
    }

    @Test
    void allowsAnotherGroupMemberToOpenTheSharedBudget() {
        String startParam = new MiniAppLaunchContextService(TOKEN)
                .createGroupStartParam(new ChatId(-100123L));
        String authorization = authorization(
                NOW.minusSeconds(30),
                43L,
                "Другой пользователь",
                Map.of("start_param", startParam)
        );

        TelegramMiniAppPrincipal principal = service.authenticate(authorization);

        assertThat(principal.userId().getId()).isEqualTo(43L);
        assertThat(principal.chatId().getId()).isEqualTo(-100123L);
    }

    @Test
    void rejectsTamperedTelegramInitData() {
        String authorization = authorization(NOW.minusSeconds(30), 42L, "Наиль")
                + "&start_param=tampered";

        assertThatThrownBy(() -> service.authenticate(authorization))
                .isInstanceOf(MiniAppUnauthorizedException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsExpiredTelegramInitData() {
        String authorization = authorization(NOW.minus(Duration.ofHours(2)), 42L, "Наиль");

        assertThatThrownBy(() -> service.authenticate(authorization))
                .isInstanceOf(MiniAppUnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsMalformedAndDuplicateAuthorizationParameters() {
        assertThatThrownBy(() -> service.authenticate("tma malformed"))
                .isInstanceOf(MiniAppUnauthorizedException.class)
                .hasMessageContaining("invalid");
        assertThatThrownBy(() -> service.authenticate("tma hash=one&hash=two"))
                .isInstanceOf(MiniAppUnauthorizedException.class)
                .hasMessageContaining("invalid");
        assertThatThrownBy(() -> service.authenticate("tma user=%ZZ"))
                .isInstanceOf(MiniAppUnauthorizedException.class)
                .hasMessageContaining("invalid");
    }

    private String authorization(Instant authDate, long userId, String firstName) {
        return authorization(authDate, userId, firstName, Map.of());
    }

    private String authorization(Instant authDate,
                                 long userId,
                                 String firstName,
                                 Map<String, String> extraValues) {
        Map<String, String> values = new TreeMap<>();
        values.put("auth_date", Long.toString(authDate.getEpochSecond()));
        values.put("query_id", "test-query");
        values.put(
                "user",
                "{\"id\":%d,\"first_name\":\"%s\",\"username\":\"tester\"}"
                        .formatted(userId, firstName)
        );
        values.putAll(extraValues);
        String dataCheckString = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secret = hmac(
                "WebAppData".getBytes(StandardCharsets.UTF_8),
                TOKEN.getBytes(StandardCharsets.UTF_8)
        );
        values.put(
                "hash",
                HexFormat.of().formatHex(hmac(secret, dataCheckString.getBytes(StandardCharsets.UTF_8)))
        );
        return "tma " + values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
