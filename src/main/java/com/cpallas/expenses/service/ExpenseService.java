package com.cpallas.expenses.service;

import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.ids.ChatId;
import com.cpallas.expenses.ids.UserId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.jpa.UserJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.ExpenseRepo;
import com.cpallas.expenses.storage.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final UserRepo userRepo;
    private final ChatRepo chatRepo;
    private final ExpenseRepo spendingRepo;

    @Transactional(rollbackFor = Exception.class)
    public void addSpending(UserId userId, ChatId chatId, String userMessage) throws WrongFormat {
        ChatJpa chat = getChat(chatId, userId);
        ExpenseJpa s = new ExpenseJpa();

        String[] text = userMessage.split(":");
        if (text.length != 2 || text[1].isBlank()) {
            throw new WrongFormat("Incorrect format, expected 'sum:description', but got: %s".formatted(userMessage));
        }
        s.setId(UUID.randomUUID());
        s.setChat(chat);
        try {
            s.setAmount(Double.valueOf(text[0]));
        } catch (NumberFormatException e) {
            throw new WrongFormat("Incorrect format, expected 'sum:description', but got: %s".formatted(userMessage));
        }
        s.setDescription(text[1]);

        spendingRepo.save(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpendingStatus getStatus(ChatId chatId, UserId userId) {
        ChatJpa chat = getChat(chatId, userId);
        List<ExpenseJpa> expenses = chat.getExpenses();
        Double spent = expenses.stream()
                .reduce(0.0, (acc, expense) -> acc + expense.getAmount(), Double::sum);

        return SpendingStatus.builder()
                .income(chat.getMonthLimit())
                .spent(spent)
                .build();
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
}
