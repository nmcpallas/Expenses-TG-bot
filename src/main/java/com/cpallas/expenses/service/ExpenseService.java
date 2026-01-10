package com.cpallas.expenses.service;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.Month;
import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.dto.ExpenseExportRow;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.*;
import com.cpallas.expenses.storage.repo.CategoryRepo;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.ExpenseRepo;
import com.cpallas.expenses.storage.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final UserRepo userRepo;
    private final ChatRepo chatRepo;
    private final ExpenseRepo expenseRepo;
    private final CategoryRepo categoryRepo;

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

        Iterable<ExpenseJpa> allExpensesBetween = expenseRepo.findAll(
                QExpenseJpa.expenseJpa.createdAt.between(now.minusDays(now.getDayOfMonth() - 1), now)
                        .and(QExpenseJpa.expenseJpa.chat.id.eq(chatId)));
        Double result = StreamSupport.stream(allExpensesBetween.spliterator(), false)
                .reduce(0.0, (acc, expense) -> acc + expense.getAmount(), Double::sum);

        return SpendingStatus.builder()
                .income(chat.getMonthLimit())
                .spent(result)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CategoryJpa> getCategories(ChatId chatId) {
        return categoryRepo.findAllByChatId(chatId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createCategory(ChatId chatId, UserId userId, String name) {
        CategoryJpa entity = new CategoryJpa();

        entity.setId(new CategoryId(UUID.randomUUID()));
        entity.setChat(getChat(chatId, userId));
        entity.setName(name);

        categoryRepo.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setOrUpdateLimitation(UserId userId, ChatId chatId, String limitationText) throws WrongFormat {
        try {
            double limit = Double.parseDouble(limitationText);

            ChatJpa chat = getChat(chatId, userId);
            chat.setMonthLimit(limit);

            chatRepo.save(chat);
        } catch (NumberFormatException e) {
            throw new WrongFormat("Incorrect format, expected number, but got: '%s'".formatted(limitationText));
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
                .orElseGet(() -> chatRepo.save(newChat(chatId, getUser(userId))));
    }

    private ChatJpa newChat(ChatId chatId, UserJpa user) {
        ChatJpa jpa = new ChatJpa();

        jpa.setId(chatId);
        jpa.setUser(user);
        jpa.setMonthLimit(0.0);

        return jpa;
    }

    private static UserJpa newUser(UserId userId) {
        UserJpa u = new UserJpa();

        u.setId(userId);

        return u;
    }
}
