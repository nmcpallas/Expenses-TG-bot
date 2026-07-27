package com.cpallas.expenses.service;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.ChatRole;
import com.cpallas.expenses.reporting.contract.ExpenseRecorded;
import com.cpallas.expenses.reporting.service.AnalyticsEventPublisher;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.jpa.ChatMemberJpa;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import com.cpallas.expenses.storage.jpa.UserJpa;
import com.cpallas.expenses.storage.repo.CategoryRepo;
import com.cpallas.expenses.storage.repo.ChatMemberRepo;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.ExpenseRepo;
import com.cpallas.expenses.storage.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final ChatId CHAT_ID = new ChatId(10L);
    private static final UserId USER_ID = new UserId(20L);

    @Mock
    private UserRepo userRepo;
    @Mock
    private ChatRepo chatRepo;
    @Mock
    private ExpenseRepo expenseRepo;
    @Mock
    private CategoryRepo categoryRepo;
    @Mock
    private ChatMemberRepo chatMemberRepo;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(
                userRepo,
                chatRepo,
                expenseRepo,
                categoryRepo,
                chatMemberRepo,
                analyticsEventPublisher
        );
    }

    @Test
    void registersNewParticipantInExistingSharedChat() {
        ChatJpa chat = chat();
        UserJpa user = new UserJpa();
        user.setId(USER_ID);
        when(chatRepo.findById(CHAT_ID)).thenReturn(Optional.of(chat));
        when(chatMemberRepo.findByChatIdAndUserId(CHAT_ID, USER_ID)).thenReturn(Optional.empty());
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepo.findAllByChatId(CHAT_ID)).thenReturn(List.of(new CategoryJpa()));

        service.getOrCreateCategories(CHAT_ID, USER_ID);

        ArgumentCaptor<ChatMemberJpa> member = ArgumentCaptor.forClass(ChatMemberJpa.class);
        verify(chatMemberRepo).save(member.capture());
        assertThat(member.getValue().getChat()).isSameAs(chat);
        assertThat(member.getValue().getUser()).isSameAs(user);
        assertThat(member.getValue().getRole()).isEqualTo(ChatRole.MEMBER);
    }

    @Test
    void rejectsCategoryFromAnotherChatWhenSavingExpense() {
        ChatJpa chat = chat();
        ChatMemberJpa member = new ChatMemberJpa();
        CategoryId foreignCategory = new CategoryId(UUID.randomUUID());
        UserSession session = new UserSession();
        session.setAmount(BigDecimal.TEN);
        session.setDescription("кофе");
        session.setCategoryId(foreignCategory);
        when(chatRepo.findById(CHAT_ID)).thenReturn(Optional.of(chat));
        when(chatMemberRepo.findByChatIdAndUserId(CHAT_ID, USER_ID))
                .thenReturn(Optional.of(member));
        when(categoryRepo.findByIdAndChatId(foreignCategory, CHAT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addSpending(USER_ID, CHAT_ID, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void savesExpenseAndEnqueuesOnlyItsDatabaseLookupKeys() {
        ChatJpa chat = chat();
        ChatMemberJpa member = new ChatMemberJpa();
        CategoryJpa category = new CategoryJpa();
        category.setId(new CategoryId(UUID.randomUUID()));
        category.setChat(chat);
        UserSession session = new UserSession();
        session.setAmount(new BigDecimal("250"));
        session.setDescription("кофе");
        session.setCategoryId(category.getId());
        when(chatRepo.findById(CHAT_ID)).thenReturn(Optional.of(chat));
        when(chatMemberRepo.findByChatIdAndUserId(CHAT_ID, USER_ID))
                .thenReturn(Optional.of(member));
        when(categoryRepo.findByIdAndChatId(category.getId(), CHAT_ID))
                .thenReturn(Optional.of(category));
        when(expenseRepo.save(any(ExpenseJpa.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseJpa saved = service.addSpending(USER_ID, CHAT_ID, session);

        ArgumentCaptor<ExpenseRecorded> event = ArgumentCaptor.forClass(ExpenseRecorded.class);
        verify(analyticsEventPublisher).publish(event.capture());
        assertThat(event.getValue().expenseId()).isEqualTo(saved.getId().getId());
        assertThat(event.getValue().chatId()).isEqualTo(CHAT_ID.getId());
    }

    private ChatJpa chat() {
        ChatJpa chat = new ChatJpa();
        chat.setId(CHAT_ID);
        return chat;
    }
}
