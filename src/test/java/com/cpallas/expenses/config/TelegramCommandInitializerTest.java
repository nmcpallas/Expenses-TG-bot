package com.cpallas.expenses.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramCommandInitializerTest {

    @Mock
    private TelegramClient telegramClient;

    @Test
    void registersHelpCommandAndCommandsMenu() throws Exception {
        new TelegramCommandInitializer(telegramClient, true).registerCommands();

        var commands = org.mockito.ArgumentCaptor.forClass(SetMyCommands.class);
        verify(telegramClient).execute(commands.capture());
        assertThat(commands.getValue().getCommands()).singleElement().satisfies(command -> {
            assertThat(command.getCommand()).isEqualTo("help");
            assertThat(command.getDescription()).isEqualTo("Как пользоваться ботом");
        });
        verify(telegramClient).execute(any(SetChatMenuButton.class));
    }

    @Test
    void doesNotCallTelegramWhenItIsDisabled() throws Exception {
        new TelegramCommandInitializer(telegramClient, false).registerCommands();

        verify(telegramClient, never()).execute(any(SetMyCommands.class));
        verify(telegramClient, never()).execute(any(SetChatMenuButton.class));
    }
}
