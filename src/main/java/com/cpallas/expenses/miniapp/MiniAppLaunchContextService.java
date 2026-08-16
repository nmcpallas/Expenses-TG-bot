package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
public class MiniAppLaunchContextService {

    private static final String VERSION = "v1";
    private static final int SIGNATURE_BYTES = 12;
    private static final int SIGNATURE_LENGTH = 16;

    private final byte[] secret;

    @Autowired
    public MiniAppLaunchContextService(@Value("${telegram.bot.token}") String botToken) {
        this.secret = botToken.getBytes(StandardCharsets.UTF_8);
    }

    public String createGroupStartParam(ChatId chatId) {
        if (chatId.getId() >= 0) {
            throw new IllegalArgumentException("Group chat id must be negative.");
        }
        String payload = chatId.getId().toString();
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return VERSION + "_" + encodedPayload + "_" + signature(encodedPayload);
    }

    public ChatId resolveChatId(String startParam, UserId userId) {
        if (startParam == null || startParam.isBlank()) {
            return new ChatId(userId.getId());
        }

        int signatureSeparator = startParam.length() - SIGNATURE_LENGTH - 1;
        if (!startParam.startsWith(VERSION + "_")
                || signatureSeparator <= VERSION.length()
                || startParam.charAt(signatureSeparator) != '_') {
            throw new MiniAppUnauthorizedException("Mini App chat context is invalid.");
        }
        String encodedPayload = startParam.substring(VERSION.length() + 1, signatureSeparator);
        String encodedSignature = startParam.substring(signatureSeparator + 1);
        byte[] receivedSignature;
        byte[] expectedSignature;
        try {
            receivedSignature = Base64.getUrlDecoder().decode(encodedSignature);
            expectedSignature = Base64.getUrlDecoder().decode(signature(encodedPayload));
        } catch (IllegalArgumentException exception) {
            throw new MiniAppUnauthorizedException("Mini App chat context is invalid.");
        }
        if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
            throw new MiniAppUnauthorizedException("Mini App chat context is invalid.");
        }

        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8
            );
            long chatId = Long.parseLong(payload);
            if (chatId >= 0) {
                throw new MiniAppUnauthorizedException("Mini App chat context is invalid.");
            }
            return new ChatId(chatId);
        } catch (MiniAppUnauthorizedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MiniAppUnauthorizedException("Mini App chat context is invalid.");
        }
    }

    private String signature(String encodedPayload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Arrays.copyOf(hmac(encodedPayload), SIGNATURE_BYTES));
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign Mini App chat context.", exception);
        }
    }
}
