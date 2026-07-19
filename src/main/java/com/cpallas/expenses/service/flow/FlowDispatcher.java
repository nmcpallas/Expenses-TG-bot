package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.GeneralMenu;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class FlowDispatcher {

    private static final InlineKeyboardMarkup GENERAL_MENU_MARKUP = GeneralMenu.init();

    private final TelegramClient telegramClient;
    private final AddCategoryFlowService addCategoryFlowService;
    private final AddExpenseFlowService addExpenseFlowService;
    private final ExcelExportFlowService excelExportFlowService;
    private final MonthLimitFlowService monthLimitFlowService;
    private final MonthStartDayFlowService monthStartDayFlowService;
    private final StatusFlowService statusFlowService;
    private final QuickExpenseFlowService quickExpenseFlowService;
    private final FlowTypeResolver flowTypeResolver;

    public void dispatch(Update update, UserSession session) throws TelegramApiException {
        switch (resolveFlow(session)) {
            case ADD_EXPENSE -> addExpenseFlowService.handle(update, session);
            case QUICK_EXPENSE -> quickExpenseFlowService.continueQuickExpense(update, session);
            case ADD_CATEGORY -> addCategoryFlowService.handle(update, session);
            case SET_MONTH_LIMIT -> monthLimitFlowService.handle(update, session);
            case SET_MONTH_START -> monthStartDayFlowService.handle(update, session);
            case DOWNLOAD_EXCEL -> excelExportFlowService.handle(update, session);
            case GENERAL_MENU -> dispatchGeneralMenuFlow(update, session);
        }
    }

    private FlowType resolveFlow(UserSession session) {
        if (session.getFlow() != null) {
            return session.getFlow();
        }

        FlowType flowType = flowTypeResolver.resolve(session.getStep());
        session.setFlow(flowType);
        return flowType;
    }

    private void dispatchGeneralMenuFlow(Update update, UserSession session) throws TelegramApiException {
        if (session.getStep() == Step.SHOW_CURRENT_STATUS) {
            statusFlowService.handle(update, session);
            return;
        }
        if (session.getStep() == Step.SHOW_GENERAL_MENU) {
            sendGeneralMenu(update, session);
            return;
        }
        telegramClient.execute(createGeneralMenuMessage(update));
    }

    private void sendGeneralMenu(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.DONE);
        telegramClient.execute(createGeneralMenuMessage(update));
    }

    private SendMessage createGeneralMenuMessage(Update update) {
        SendMessage message = createMessage("Выберите дальнейшее действие", getChatIdFromUpdate(update));
        message.setReplyMarkup(GENERAL_MENU_MARKUP);
        return message;
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }
}
