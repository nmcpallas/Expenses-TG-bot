package com.cpallas.expenses.service;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.enums.ChatRole;
import com.cpallas.expenses.reporting.contract.ExpenseRecorded;
import com.cpallas.expenses.reporting.service.AnalyticsEventPublisher;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatMemberId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.*;
import com.cpallas.expenses.storage.repo.CategoryRepo;
import com.cpallas.expenses.storage.repo.ChatMemberRepo;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.ExpenseRepo;
import com.cpallas.expenses.storage.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Продукты",
            "Кафе",
            "Транспорт",
            "Дом",
            "Здоровье",
            "Развлечения",
            "Покупки",
            "Другое"
    );

    private final UserRepo userRepo;
    private final ChatRepo chatRepo;
    private final ExpenseRepo expenseRepo;
    private final CategoryRepo categoryRepo;
    private final ChatMemberRepo chatMemberRepo;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    @Value("${expense.reporting.zone:Asia/Tashkent}")
    private String reportingZone;

    @Transactional(rollbackFor = Exception.class)
    public ExpenseJpa addSpending(UserId userId, ChatId chatId, UserSession userSession) {
        ChatJpa chat = getChat(chatId, userId);
        ExpenseJpa s = new ExpenseJpa();

        s.setId(new ExpenseId(UUID.randomUUID()));
        s.setChat(chat);
        s.setAmount(userSession.getAmount());
        s.setDescription(userSession.getDescription());
        s.setCategory(categoryRepo.findByIdAndChatId(userSession.getCategoryId(), chatId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category does not belong to this chat."
                )));

        ExpenseJpa saved = expenseRepo.save(s);
        analyticsEventPublisher.publish(ExpenseRecorded.of(
                saved.getId().getId(),
                chatId.getId()
        ));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public SpendingStatus getStatus(ChatId chatId, UserId userId) {
        ZonedDateTime now = ZonedDateTime.now(reportingZone());
        ChatJpa chat = getChat(chatId, userId);

        int monthStartDay = chat.getMonthStart();

        LocalDate today = now.toLocalDate();
        ZoneId zone = now.getZone();

        LocalDate periodStartDate;
        LocalDate periodEndDate;

        if (today.getDayOfMonth() >= monthStartDay) {
            periodStartDate = today.withDayOfMonth(monthStartDay);
            periodEndDate = periodStartDate.plusMonths(1);
        } else {
            periodEndDate = today.withDayOfMonth(monthStartDay);
            periodStartDate = periodEndDate.minusMonths(1);
        }

        ZonedDateTime from = periodStartDate.atStartOfDay(zone);
        ZonedDateTime to = periodEndDate.atStartOfDay(zone).minusNanos(1);

        Iterable<ExpenseJpa> expenses = expenseRepo.findAll(
                QExpenseJpa.expenseJpa.createdAt.between(from, to)
                        .and(QExpenseJpa.expenseJpa.chat.id.eq(chatId))
        );

        BigDecimal spent = StreamSupport.stream(expenses.spliterator(), false)
                .map(ExpenseJpa::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expensesByCategory = StreamSupport.stream(expenses.spliterator(), false)
                .collect(Collectors.groupingBy(
                        $ -> $.getCategory().getName(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                ExpenseJpa::getAmount,
                                BigDecimal::add
                        )
                ));

        Map<String, BigDecimal> limitsByCategory = categoryRepo.findAllByChatId(chatId).stream()
                .filter(category -> category.getSpendingLimit() != null)
                .collect(Collectors.toMap(
                        CategoryJpa::getName,
                        CategoryJpa::getSpendingLimit
                ));

        return new SpendingStatus(chat.getMonthLimit(),
                spent,
                expensesByCategory,
                limitsByCategory);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CategoryJpa> getOrCreateCategories(ChatId chatId, UserId userId) {
        ChatJpa chat = getChat(chatId, userId);
        List<CategoryJpa> existing = categoryRepo.findAllByChatId(chatId);
        if (!existing.isEmpty()) {
            return existing;
        }

        return DEFAULT_CATEGORIES.stream()
                .map(name -> newCategory(chat, name))
                .map(categoryRepo::save)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryJpa createCategory(ChatId chatId, UserId userId, String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank.");
        }
        Optional<CategoryJpa> existing = categoryRepo.findAllByChatId(chatId).stream()
                .filter(category -> category.getName().equalsIgnoreCase(normalizedName))
                .findFirst();
        return existing.orElseGet(() -> categoryRepo.save(
                newCategory(getChat(chatId, userId), normalizedName)
        ));
    }

    @Transactional
    public Optional<ExpenseJpa> getLastExpense(ChatId chatId, UserId userId) {
        getChat(chatId, userId);
        return expenseRepo.findFirstByChat_IdOrderByCreatedAtDesc(chatId);
    }

    @Transactional
    public Optional<ExpenseJpa> getExpense(ChatId chatId, UserId userId, ExpenseId expenseId) {
        getChat(chatId, userId);
        return expenseRepo.findByIdAndChat_Id(expenseId, chatId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<ExpenseJpa> deleteLastExpense(ChatId chatId, UserId userId) {
        getChat(chatId, userId);
        Optional<ExpenseJpa> expense = expenseRepo.findFirstByChat_IdOrderByCreatedAtDesc(chatId);
        expense.ifPresent(expenseRepo::delete);
        return expense;
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<ExpenseJpa> deleteExpense(ChatId chatId, UserId userId, ExpenseId expenseId) {
        getChat(chatId, userId);
        Optional<ExpenseJpa> expense = expenseRepo.findByIdAndChat_Id(expenseId, chatId);
        expense.ifPresent(expenseRepo::delete);
        return expense;
    }

    @Transactional(rollbackFor = Exception.class)
    public ExpenseJpa updateExpenseAmount(ChatId chatId,
                                          UserId userId,
                                          ExpenseId expenseId,
                                          BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive.");
        }
        ExpenseJpa expense = requiredExpense(chatId, userId, expenseId);
        expense.setAmount(amount);
        return expenseRepo.save(expense);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExpenseJpa updateExpenseDescription(ChatId chatId,
                                               UserId userId,
                                               ExpenseId expenseId,
                                               String description) {
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.isBlank()) {
            throw new IllegalArgumentException("Expense description must not be blank.");
        }
        ExpenseJpa expense = requiredExpense(chatId, userId, expenseId);
        expense.setDescription(normalizedDescription);
        return expenseRepo.save(expense);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExpenseJpa updateExpenseCategory(ChatId chatId,
                                            UserId userId,
                                            ExpenseId expenseId,
                                            CategoryId categoryId) {
        ExpenseJpa expense = requiredExpense(chatId, userId, expenseId);
        CategoryJpa category = categoryRepo.findByIdAndChatId(categoryId, chatId)
                .orElseThrow(() -> new IllegalArgumentException("Category does not belong to this chat."));
        expense.setCategory(category);
        return expenseRepo.save(expense);
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryJpa updateCategoryLimit(ChatId chatId,
                                           UserId userId,
                                           CategoryId categoryId,
                                           BigDecimal limit) {
        getChat(chatId, userId);
        if (limit != null && limit.signum() < 0) {
            throw new IllegalArgumentException("Category limit must not be negative.");
        }
        CategoryJpa category = categoryRepo.findByIdAndChatId(categoryId, chatId)
                .orElseThrow(() -> new IllegalArgumentException("Category does not belong to this chat."));
        category.setSpendingLimit(limit == null || limit.signum() == 0 ? null : limit);
        return categoryRepo.save(category);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatJpa updateBudgetSettings(ChatId chatId,
                                        UserId userId,
                                        BigDecimal monthLimit,
                                        Integer monthStart,
                                        Boolean weeklyReportEnabled,
                                        Boolean unusualNotificationsEnabled) {
        ChatJpa chat = getChat(chatId, userId);
        if (monthLimit != null) {
            if (monthLimit.signum() < 0) {
                throw new IllegalArgumentException("Month limit must not be negative.");
            }
            chat.setMonthLimit(monthLimit);
        }
        if (monthStart != null) {
            if (monthStart < 1 || monthStart > 28) {
                throw new IllegalArgumentException("Month start must be between 1 and 28.");
            }
            chat.setMonthStart(monthStart);
        }
        if (weeklyReportEnabled != null) {
            chat.setWeeklyReportEnabled(weeklyReportEnabled);
        }
        if (unusualNotificationsEnabled != null) {
            chat.setUnusualNotificationsEnabled(unusualNotificationsEnabled);
        }
        return chatRepo.save(chat);
    }

    @Transactional
    public ChatJpa getBudgetSettings(ChatId chatId, UserId userId) {
        return getChat(chatId, userId);
    }

    @Transactional
    public List<ExpenseJpa> getRecentExpenses(ChatId chatId, UserId userId, int limit) {
        getChat(chatId, userId);
        return expenseRepo.findAll(
                        QExpenseJpa.expenseJpa.chat.id.eq(chatId),
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .getContent();
    }

    private UserJpa getUser(UserId userId) {
        return userRepo.findById(userId)
                .orElseGet(() -> userRepo.save(newUser(userId)));
    }

    private ChatJpa getChat(ChatId chatId, UserId userId) {
        return chatRepo.findById(chatId)
                .map(chat -> {
                    if (chatMemberRepo.findByChatIdAndUserId(chatId, userId).isEmpty()) {
                        addChatMember(chat, getUser(userId), ChatRole.MEMBER);
                    }
                    return chat;
                })
                .orElseGet(() -> createChatWithOwner(chatId, userId));
    }

    private ChatJpa createChatWithOwner(ChatId chatId, UserId userId) {
        UserJpa user = getUser(userId);
        ChatJpa chat = chatRepo.save(newChat(chatId, user));

        addChatMember(chat, user, ChatRole.OWNER);

        return chat;
    }

    private ChatJpa newChat(ChatId chatId, UserJpa user) {
        ChatJpa jpa = new ChatJpa();

        jpa.setId(chatId);
        jpa.setUser(user);
        jpa.setMonthLimit(BigDecimal.ZERO);
        jpa.setMonthStart(1);
        jpa.setWeeklyReportEnabled(true);
        jpa.setUnusualNotificationsEnabled(true);

        return jpa;
    }

    private CategoryJpa newCategory(ChatJpa chat, String name) {
        CategoryJpa entity = new CategoryJpa();
        entity.setId(new CategoryId(UUID.randomUUID()));
        entity.setChat(chat);
        entity.setName(name);
        return entity;
    }

    private void addChatMember(ChatJpa chat, UserJpa user, ChatRole role) {
        ChatMemberJpa member = new ChatMemberJpa();
        member.setId(new ChatMemberId(UUID.randomUUID()));
        member.setChat(chat);
        member.setUser(user);
        member.setRole(role);
        chatMemberRepo.save(member);
    }

    private ZoneId reportingZone() {
        return ZoneId.of(reportingZone);
    }

    private ExpenseJpa requiredExpense(ChatId chatId, UserId userId, ExpenseId expenseId) {
        getChat(chatId, userId);
        return expenseRepo.findByIdAndChat_Id(expenseId, chatId)
                .orElseThrow(() -> new IllegalArgumentException("Expense was not found."));
    }

    private static UserJpa newUser(UserId userId) {
        UserJpa u = new UserJpa();

        u.setId(userId);

        return u;
    }
}
