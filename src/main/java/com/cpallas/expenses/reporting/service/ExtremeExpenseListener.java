package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.controller.dto.ExpenseActionMenu;
import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.ExtremeExpenseDetected;
import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.repo.ExtremeExpenseDeliveryRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZonedDateTime;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtremeExpenseListener {

    private final ExtremeExpenseDeliveryRepo deliveryRepo;
    private final TelegramClient telegramClient;

    @RabbitListener(queues = ReportingRabbitConfig.EXTREME_EXPENSE_QUEUE)
    public void receive(ExtremeExpenseDetected event) throws Exception {
        if (deliveryRepo.claim(
                event.eventId(),
                event.expenseId(),
                event.chatId(),
                ZonedDateTime.now()
        ) == 0) {
            log.info("Skipping duplicate extreme expense event: eventId={}", event.eventId());
            return;
        }

        try {
            SendMessage notice = createMessage(
                    """
                    ⚡ Необычная трата

                    %s · %s
                    Это примерно в %s раза выше вашего обычного расхода в этой категории (%s).
                    """.formatted(
                            event.amount().stripTrailingZeros().toPlainString(),
                            event.categoryName(),
                            event.multiplier().stripTrailingZeros().toPlainString(),
                            event.usualAmount().stripTrailingZeros().toPlainString()
                    ).trim(),
                    event.chatId()
            );
            notice.setReplyMarkup(ExpenseActionMenu.afterSave(new ExpenseId(event.expenseId())));
            telegramClient.execute(notice);
            deliveryRepo.markDelivered(event.eventId(), ZonedDateTime.now());
        } catch (Exception exception) {
            deliveryRepo.release(event.eventId());
            throw exception;
        }
    }
}
