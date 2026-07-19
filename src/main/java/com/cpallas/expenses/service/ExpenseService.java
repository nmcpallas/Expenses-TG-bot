package com.cpallas.expenses.service;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.Month;
import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.enums.ChatRole;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.dto.ExpenseExportRow;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final UserRepo userRepo;
    private final ChatRepo chatRepo;
    private final ExpenseRepo expenseRepo;
    private final CategoryRepo categoryRepo;
    private final ChatMemberRepo chatMemberRepo;

    @Transactional(rollbackFor = Exception.class)
    public void addSpending(UserId userId, ChatId chatId, UserSession userSession) {
        ChatJpa chat = getChat(chatId, userId);
        ExpenseJpa s = new ExpenseJpa();

        s.setId(new ExpenseId(UUID.randomUUID()));
        s.setChat(chat);
        s.setAmount(userSession.getAmount());
        s.setDescription(userSession.getDescription());
        s.setCategory(categoryRepo.findById(userSession.getCategoryId()).orElseThrow());

        expenseRepo.save(s);
    }

    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public SpendingStatus getStatus(ChatId chatId, UserId userId) {
        ZonedDateTime now = ZonedDateTime.now();
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

        return new SpendingStatus(chat.getMonthLimit(),
                spent,
                expensesByCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryJpa> getCategories(ChatId chatId) {
        return categoryRepo.findAllByChatId(chatId);
    }

    @Transactional(readOnly = true)
    public long countExpenses(ChatId chatId) {
        return expenseRepo.count(QExpenseJpa.expenseJpa.chat.id.eq(chatId));
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryJpa createCategory(ChatId chatId, UserId userId, String name) {
        CategoryJpa entity = new CategoryJpa();

        entity.setId(new CategoryId(UUID.randomUUID()));
        entity.setChat(getChat(chatId, userId));
        entity.setName(name);

        return categoryRepo.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setOrUpdateLimitation(UserId userId, ChatId chatId, String limitationText) throws WrongFormat {
        try {
            ChatJpa chat = getChat(chatId, userId);
            chat.setMonthLimit(new BigDecimal(limitationText));

            chatRepo.save(chat);
        } catch (NumberFormatException e) {
            throw new WrongFormat("Incorrect format, expected number, but got: '%s'".formatted(limitationText));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveInputStartDay(UserId userId, ChatId chatId, String day) throws WrongFormat {
        try {
            ChatJpa chat = getChat(chatId, userId);
            chat.setMonthStart(Integer.parseInt(day.trim()));

            chatRepo.save(chat);
        } catch (NumberFormatException e) {
            throw new WrongFormat("Incorrect format, expected number, but got: '%s'".formatted(day));
        }
    }

    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public List<ExpenseExportRow> getExpenses(ChatId chatId, Month month) {
        ZonedDateTime firstDayOfMonth = getFirstDayOfMonth(month);
        ZonedDateTime lastDayOfMonth = getLastDayOfMonth(month);

        return StreamSupport.stream(
                expenseRepo.findAll(QExpenseJpa.expenseJpa.createdAt.between(firstDayOfMonth, lastDayOfMonth)
                        .and(QExpenseJpa.expenseJpa.chat.id.eq(chatId))).spliterator(),
                false
        ).map($ -> new ExpenseExportRow($.getAmount(), $.getCategory() == null ? "" : $.getCategory().getName(), $.getDescription(), $.getCreatedAt()))
                .toList();
    }

    private ZonedDateTime getFirstDayOfMonth(Month month) {
        return ZonedDateTime.now()
                .withMonth(month.ordinal() + 1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    private ZonedDateTime getLastDayOfMonth(Month month) {
        return getFirstDayOfMonth(month)
                .plusMonths(1)
                .minusNanos(1);
    }


    private UserJpa getUser(UserId userId) {
        return userRepo.findById(userId)
                .orElseGet(() -> userRepo.save(newUser(userId)));
    }

    private ChatJpa getChat(ChatId chatId, UserId userId) {
        return chatRepo.findById(chatId)
                .orElseGet(() -> createChatWithOwner(chatId, userId));
    }

    private ChatJpa createChatWithOwner(ChatId chatId, UserId userId) {
        UserJpa user = getUser(userId);
        ChatJpa chat = chatRepo.save(newChat(chatId, user));

        ChatMemberJpa member = new ChatMemberJpa();
        member.setId(new ChatMemberId(UUID.randomUUID()));
        member.setChat(chat);
        member.setUser(user);
        member.setRole(ChatRole.OWNER);
        chatMemberRepo.save(member);

        return chat;
    }

    private ChatJpa newChat(ChatId chatId, UserJpa user) {
        ChatJpa jpa = new ChatJpa();

        jpa.setId(chatId);
        jpa.setUser(user);
        jpa.setMonthLimit(BigDecimal.ZERO);
        jpa.setMonthStart(1);

        return jpa;
    }

    private static UserJpa newUser(UserId userId) {
        UserJpa u = new UserJpa();

        u.setId(userId);

        return u;
    }
}
