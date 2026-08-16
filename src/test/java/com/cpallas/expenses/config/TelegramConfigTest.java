package com.cpallas.expenses.config;

import com.cpallas.expenses.controller.TelegramController;
import com.cpallas.expenses.miniapp.MiniAppLaunchContextService;
import com.cpallas.expenses.service.flow.ExpenseActionFlowService;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelegramConfigTest {

    @Test
    void doesNotRegisterLongPollingBotWhenTelegramIsDisabled() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThat(context.getBeansOfType(TelegramController.class)).isEmpty();
        }
    }

    @Test
    void registersLongPollingBotWhenTelegramIsEnabled() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertThat(context.getBeansOfType(TelegramController.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext context(boolean telegramEnabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of(
                        "telegram.enabled", Boolean.toString(telegramEnabled),
                        "telegram.bot.token", "test-token",
                        "telegram.bot.username", "test_bot",
                        "expense.mini-app.url", "https://example.test",
                        "system.dispatcher-capacity", "10",
                        "system.chat-blocking-duration", "1"
                )
        ));
        context.registerBean(QuickExpenseFlowService.class, () -> mock(QuickExpenseFlowService.class));
        context.registerBean(ExpenseActionFlowService.class, () -> mock(ExpenseActionFlowService.class));
        context.registerBean(MiniAppLaunchContextService.class, () -> mock(MiniAppLaunchContextService.class));
        context.register(TelegramConfig.class);
        context.refresh();
        return context;
    }
}
