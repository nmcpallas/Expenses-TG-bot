package com.cpallas.expenses.service.access;

import com.cpallas.expenses.enums.*;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.EntitlementJpa;
import com.cpallas.expenses.storage.jpa.SubscriptionJpa;
import com.cpallas.expenses.storage.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final UserRepo userRepo;
    private final ChatMemberRepo chatMemberRepo;
    private final SubscriptionMemberRepo subscriptionMemberRepo;
    private final SubscriptionChatRepo subscriptionChatRepo;
    private final EntitlementRepo entitlementRepo;

    @Transactional(readOnly = true)
    public boolean hasFeature(UserId userId, ChatId chatId, Feature feature) {
        if (isGlobalAdmin(userId)) {
            return true;
        }
        if (hasBooleanEntitlement(EntitlementSubjectType.USER, userId.getId().toString(), feature)) {
            return true;
        }
        if (hasBooleanEntitlement(EntitlementSubjectType.CHAT, chatId.getId().toString(), feature)) {
            return true;
        }
        return activeSubscriptions(userId, chatId).stream()
                .anyMatch(subscription -> hasBooleanEntitlement(
                        EntitlementSubjectType.SUBSCRIPTION,
                        subscription.getId().getId().toString(),
                        feature
                ));
    }

    @Transactional(readOnly = true)
    public Optional<Integer> getLimit(UserId userId, ChatId chatId, Feature feature) {
        if (isGlobalAdmin(userId)) {
            return Optional.empty();
        }
        return firstLimitEntitlement(EntitlementSubjectType.USER, userId.getId().toString(), feature)
                .or(() -> firstLimitEntitlement(EntitlementSubjectType.CHAT, chatId.getId().toString(), feature))
                .or(() -> activeSubscriptions(userId, chatId).stream()
                        .map(subscription -> firstLimitEntitlement(
                                EntitlementSubjectType.SUBSCRIPTION,
                                subscription.getId().getId().toString(),
                                feature
                        ))
                        .flatMap(Optional::stream)
                        .findFirst());
    }

    @Transactional(readOnly = true)
    public void requireChatRole(UserId userId, ChatId chatId, ChatRole... allowedRoles) {
        if (isGlobalAdmin(userId)) {
            return;
        }

        ChatRole role = chatMemberRepo.findByChatIdAndUserId(chatId, userId)
                .map($ -> $.getRole())
                .orElseThrow(() -> new AccessDeniedException("User is not a chat member."));

        for (ChatRole allowedRole : allowedRoles) {
            if (role == allowedRole) {
                return;
            }
        }
        throw new AccessDeniedException("User does not have required chat role.");
    }

    @Transactional(readOnly = true)
    public void requireFeature(UserId userId, ChatId chatId, Feature feature) {
        if (!hasFeature(userId, chatId, feature)) {
            throw new AccessDeniedException("Feature is not available.");
        }
    }

    private boolean isGlobalAdmin(UserId userId) {
        return userRepo.findById(userId)
                .map(user -> user.getGlobalRole() == GlobalRole.ADMIN)
                .orElse(false);
    }

    private List<SubscriptionJpa> activeSubscriptions(UserId userId, ChatId chatId) {
        List<SubscriptionJpa> userSubscriptions = subscriptionMemberRepo.findAllByUserId(userId).stream()
                .map($ -> $.getSubscription())
                .filter(this::isActive)
                .toList();
        List<SubscriptionJpa> chatSubscriptions = subscriptionChatRepo.findAllByChatId(chatId).stream()
                .map($ -> $.getSubscription())
                .filter(this::isActive)
                .toList();

        return java.util.stream.Stream.concat(userSubscriptions.stream(), chatSubscriptions.stream())
                .distinct()
                .toList();
    }

    private boolean isActive(SubscriptionJpa subscription) {
        return subscription.getStatus() == SubscriptionStatus.ACTIVE
                && (subscription.getValidUntil() == null || subscription.getValidUntil().isAfter(ZonedDateTime.now()));
    }

    private boolean hasBooleanEntitlement(EntitlementSubjectType subjectType, String subjectId, Feature feature) {
        return entitlementRepo.findAllBySubjectTypeAndSubjectIdAndFeatureAndEnabledTrue(subjectType, subjectId, feature).stream()
                .filter(this::isCurrentlyValid)
                .filter(entitlement -> entitlement.getValueType() == EntitlementValueType.BOOLEAN)
                .anyMatch(entitlement -> Boolean.parseBoolean(entitlement.getValue()));
    }

    private Optional<Integer> firstLimitEntitlement(EntitlementSubjectType subjectType, String subjectId, Feature feature) {
        return entitlementRepo.findAllBySubjectTypeAndSubjectIdAndFeatureAndEnabledTrue(subjectType, subjectId, feature).stream()
                .filter(this::isCurrentlyValid)
                .filter(entitlement -> entitlement.getValueType() == EntitlementValueType.LIMIT)
                .map(EntitlementJpa::getValue)
                .map(Integer::parseInt)
                .findFirst();
    }

    private boolean isCurrentlyValid(EntitlementJpa entitlement) {
        ZonedDateTime now = ZonedDateTime.now();
        boolean started = entitlement.getValidFrom() == null || !entitlement.getValidFrom().isAfter(now);
        boolean notExpired = entitlement.getValidUntil() == null || entitlement.getValidUntil().isAfter(now);
        return started && notExpired;
    }
}
