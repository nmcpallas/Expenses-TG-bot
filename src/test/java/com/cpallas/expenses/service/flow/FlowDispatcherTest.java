package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FlowDispatcherTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private AddCategoryFlowService addCategoryFlowService;
    @Mock
    private AddExpenseFlowService addExpenseFlowService;
    @Mock
    private ExcelExportFlowService excelExportFlowService;
    @Mock
    private MonthLimitFlowService monthLimitFlowService;
    @Mock
    private MonthStartDayFlowService monthStartDayFlowService;
    @Mock
    private StatusFlowService statusFlowService;
    @Mock
    private QuickExpenseFlowService quickExpenseFlowService;

    private FlowDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new FlowDispatcher(
                telegramClient,
                addCategoryFlowService,
                addExpenseFlowService,
                excelExportFlowService,
                monthLimitFlowService,
                monthStartDayFlowService,
                statusFlowService,
                quickExpenseFlowService,
                new FlowTypeResolver()
        );
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "START_ADD_EXPENSE",
            "AWAITING_EXPENSE_AMOUNT",
            "AWAITING_EXPENSE_CATEGORY",
            "AWAITING_EXPENSE_DESCRIPTION"
    })
    void dispatchesAddExpenseSteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.ADD_EXPENSE);

        dispatcher.dispatch(update, session);

        verify(addExpenseFlowService).handle(update, session);
    }

    @Test
    void dispatchesStatusFlow() throws TelegramApiException {
        Update update = messageUpdate();
        UserSession statusSession = session(Step.SHOW_CURRENT_STATUS, FlowType.GENERAL_MENU);

        dispatcher.dispatch(update, statusSession);

        verify(statusFlowService).handle(update, statusSession);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "AWAITING_QUICK_EXPENSE_CATEGORY",
            "AWAITING_QUICK_EXPENSE_CATEGORY_NAME"
    })
    void dispatchesQuickExpenseSteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.QUICK_EXPENSE);

        dispatcher.dispatch(update, session);

        verify(quickExpenseFlowService).continueQuickExpense(update, session);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "START_SET_MONTH_START_DAY",
            "AWAITING_MONTH_START_DAY"
    })
    void dispatchesMonthStartDaySteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.SET_MONTH_START);

        dispatcher.dispatch(update, session);

        verify(monthStartDayFlowService).handle(update, session);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "START_SET_MONTH_LIMIT",
            "AWAITING_MONTH_LIMIT"
    })
    void dispatchesMonthLimitSteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.SET_MONTH_LIMIT);

        dispatcher.dispatch(update, session);

        verify(monthLimitFlowService).handle(update, session);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "START_ADD_CATEGORY",
            "AWAITING_CATEGORY_NAME"
    })
    void dispatchesAddCategorySteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.ADD_CATEGORY);

        dispatcher.dispatch(update, session);

        verify(addCategoryFlowService).handle(update, session);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {
            "START_DOWNLOAD_EXCEL",
            "AWAITING_EXCEL_MONTH"
    })
    void dispatchesExcelSteps(Step step) throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = session(step, FlowType.DOWNLOAD_EXCEL);

        dispatcher.dispatch(update, session);

        verify(excelExportFlowService).handle(update, session);
    }

    @Test
    void sendsGeneralMenuAndCompletesSession() throws TelegramApiException {
        UserSession session = session(Step.SHOW_GENERAL_MENU, FlowType.GENERAL_MENU);

        dispatcher.dispatch(messageUpdate(), session);

        assertThat(session.getStep()).isEqualTo(Step.DONE);
        verify(telegramClient).execute(any(SendMessage.class));
        verifyNoInteractions(
                addCategoryFlowService,
                addExpenseFlowService,
                excelExportFlowService,
                monthLimitFlowService,
                monthStartDayFlowService,
                statusFlowService,
                quickExpenseFlowService
        );
    }

    @Test
    void resolvesMissingFlowFromStep() throws TelegramApiException {
        Update update = messageUpdate();
        UserSession session = new UserSession(Step.START_ADD_EXPENSE, null, null, null, null, null);

        dispatcher.dispatch(update, session);

        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_EXPENSE);
        verify(addExpenseFlowService).handle(update, session);
    }

    private UserSession session(Step step, FlowType flowType) {
        return new UserSession(step, flowType, null, null, null, null);
    }

    private Update messageUpdate() {
        Update update = new Update();
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        update.setMessage(message);
        return update;
    }
}
