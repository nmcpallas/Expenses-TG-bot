package com.cpallas.expenses.admin;

import com.cpallas.expenses.storage.repo.ChatRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBroadcastServiceTest {

    @Mock
    private ChatRepo chatRepo;
    @Mock
    private TelegramClient telegramClient;

    @Test
    void sendsMessageToEveryChatWithoutStoppingOnDeliveryFailure() throws Exception {
        when(chatRepo.findAllChatIds()).thenReturn(List.of(10L, 20L, 30L));
        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(null)
                .thenThrow(new TelegramApiException("bot was blocked"))
                .thenReturn(null);

        AdminBroadcastDtos.Result result =
                new AdminBroadcastService(chatRepo, telegramClient).broadcast("  Технические работы  ");

        ArgumentCaptor<SendMessage> messages = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(3)).execute(messages.capture());
        assertThat(messages.getAllValues())
                .extracting(SendMessage::getChatId)
                .containsExactly("10", "20", "30");
        assertThat(messages.getAllValues())
                .extracting(SendMessage::getText)
                .containsOnly("Технические работы");
        assertThat(result.totalChats()).isEqualTo(3);
        assertThat(result.sent()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.chatId()).isEqualTo(20L);
                    assertThat(failure.error()).contains("bot was blocked");
                });
    }

    @Test
    void rejectsBlankAndOversizedMessagesBeforeLoadingChats() {
        AdminBroadcastService service = new AdminBroadcastService(chatRepo, telegramClient);

        assertThatThrownBy(() -> service.broadcast("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> service.broadcast("a".repeat(4097)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096");
    }
}
