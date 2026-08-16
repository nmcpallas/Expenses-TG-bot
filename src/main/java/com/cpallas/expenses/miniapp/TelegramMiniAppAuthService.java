package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.storage.ids.UserId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TelegramMiniAppAuthService {

    private static final String AUTH_SCHEME = "tma ";
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

    private final String botToken;
    private final Duration maxAge;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MiniAppLaunchContextService launchContextService;

    @Autowired
    public TelegramMiniAppAuthService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${expense.mini-app.auth-max-age-seconds:3600}") long maxAgeSeconds,
            ObjectMapper objectMapper
    ) {
        this(
                botToken,
                Duration.ofSeconds(maxAgeSeconds),
                objectMapper,
                Clock.systemUTC(),
                new MiniAppLaunchContextService(botToken)
        );
    }

    TelegramMiniAppAuthService(
            String botToken,
            Duration maxAge,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                botToken,
                maxAge,
                objectMapper,
                clock,
                new MiniAppLaunchContextService(botToken)
        );
    }

    TelegramMiniAppAuthService(
            String botToken,
            Duration maxAge,
            ObjectMapper objectMapper,
            Clock clock,
            MiniAppLaunchContextService launchContextService
    ) {
        this.botToken = botToken;
        this.maxAge = maxAge;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.launchContextService = launchContextService;
    }

    public TelegramMiniAppPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(AUTH_SCHEME)) {
            throw new MiniAppUnauthorizedException("Telegram authorization is missing.");
        }
        Map<String, String> parameters;
        try {
            parameters = parse(authorization.substring(AUTH_SCHEME.length()));
        } catch (IllegalArgumentException exception) {
            throw new MiniAppUnauthorizedException("Telegram authorization is invalid.");
        }
        String receivedHash = parameters.remove("hash");
        if (receivedHash == null || !isValidHash(parameters, receivedHash)) {
            throw new MiniAppUnauthorizedException("Telegram authorization signature is invalid.");
        }

        Instant authenticatedAt = parseAuthDate(parameters.get("auth_date"));
        Instant now = clock.instant();
        if (authenticatedAt.isBefore(now.minus(maxAge))
                || authenticatedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new MiniAppUnauthorizedException("Telegram authorization has expired.");
        }

        TelegramMiniAppPrincipal user = parseUser(parameters.get("user"));
        return new TelegramMiniAppPrincipal(
                user.userId(),
                launchContextService.resolveChatId(parameters.get("start_param"), user.userId()),
                user.firstName(),
                user.username()
        );
    }

    private Map<String, String> parse(String initData) {
        Map<String, String> parameters = new TreeMap<>();
        for (String item : initData.split("&")) {
            int separator = item.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Malformed Telegram authorization parameter.");
            }
            String key = URLDecoder.decode(item.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(item.substring(separator + 1), StandardCharsets.UTF_8);
            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate Telegram authorization parameter.");
            }
        }
        return parameters;
    }

    private boolean isValidHash(Map<String, String> parameters, String receivedHash) {
        String dataCheckString = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secretKey = hmac(
                "WebAppData".getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8)
        );
        byte[] expected = hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        byte[] received;
        try {
            received = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(expected, received);
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate Telegram authorization.", exception);
        }
    }

    private Instant parseAuthDate(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw new MiniAppUnauthorizedException("Telegram authorization date is invalid.");
        }
    }

    private TelegramMiniAppPrincipal parseUser(String value) {
        try {
            JsonNode user = objectMapper.readTree(value);
            long id = user.path("id").asLong(0);
            if (id <= 0) {
                throw new MiniAppUnauthorizedException("Telegram user is missing.");
            }
            return new TelegramMiniAppPrincipal(
                    new UserId(id),
                    null,
                    user.path("first_name").asText(""),
                    user.path("username").asText("")
            );
        } catch (MiniAppUnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MiniAppUnauthorizedException("Telegram user is invalid.");
        }
    }
}
