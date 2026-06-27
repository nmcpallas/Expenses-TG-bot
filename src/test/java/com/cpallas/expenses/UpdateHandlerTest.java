package com.cpallas.expenses;

import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.controller.handler.UpdateHandler;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@TestInstance( TestInstance.Lifecycle.PER_METHOD)
public class UpdateHandlerTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private QuickExpenseFlowService quickExpenseFlowService;

    //ввести трату без категорий -> ввести категорию -> категория сохранилась
    @Test
    void saveCategoryDuringExpenseSave() throws TelegramApiException {
        Mockito.when(telegramClient.execute(Mockito.any(AnswerCallbackQuery.class))).thenReturn(null);
        Mockito.when(expenseService.getCategories(Mockito.any())).thenReturn(Collections.emptyList());

        UpdateHandler updateHandler = newHandler();

        Update updateToSaveExpense = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(Step.SAVING_EXPENSE.name());
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        callbackQuery.setMessage(message);
        callbackQuery.setId("testCallbackQueryId");
        updateToSaveExpense.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToSaveExpense);

        assertThat(getSendMessage().getFirst().getText())
                .isEqualTo("Отправьте потраченную сумму");

        Update updateWithAmount = new Update();
        Message messageWithAmount = new Message();
        messageWithAmount.setChat(new Chat(1L, "test"));
        messageWithAmount.setText("100");
        updateWithAmount.setMessage(messageWithAmount);

        updateHandler.handle(updateWithAmount);

        Mockito.verify(expenseService, Mockito.times(1)).getCategories(Mockito.any());
        SendMessage messageFromBot = getSendMessage().get(1);
        assertThat(messageFromBot.getText())
                .isEqualTo("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату");
        InlineKeyboardMarkup replyMarkup = (InlineKeyboardMarkup) messageFromBot.getReplyMarkup();
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Добавить категорию");
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getCallbackData()).isEqualTo(Step.CREATING_EXPENSE_CATEGORY.name());

        callbackQuery.setData(Step.CREATING_EXPENSE_CATEGORY.name());
        updateToSaveExpense.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToSaveExpense);

        assertThat(getSendMessage().get(2).getText())
                .isEqualTo("Введите название категории");

        Update updateWithCategory = new Update();
        Message messageWithCategory = new Message();
        messageWithCategory.setFrom(new User(1L, "test", false));
        messageWithCategory.setChat(new Chat(1L, "test"));
        messageWithCategory.setText("testCategory");
        updateWithCategory.setMessage(messageWithCategory);

        updateHandler.handle(updateWithCategory);

        Mockito.verify(expenseService, Mockito.times(1)).createCategory(Mockito.any(ChatId.class), Mockito.any(UserId.class), eq(updateWithCategory.getMessage().getText()));
        assertThat(getSendMessage().get(3).getText()).isEqualTo("Категория успешно добавлена");

        newSessionAfterEachGeneralMenu(updateHandler, () -> {
            try {
                return getSendMessage().get(4);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void saveExpense() throws TelegramApiException {
        Mockito.when(telegramClient.execute(Mockito.any(AnswerCallbackQuery.class))).thenReturn(null);
        CategoryJpa category = new CategoryJpa();
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(1L));
        category.setChat(chat);
        category.setName("testCategory");
        category.setId(new CategoryId(UUID.randomUUID()));
        Mockito.when(expenseService.getCategories(Mockito.any())).thenReturn(List.of(category));

        UpdateHandler updateHandler = newHandler();

        Update updateToSaveExpense = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(Step.SAVING_EXPENSE.name());
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        callbackQuery.setMessage(message);
        callbackQuery.setId("testCallbackQueryId");
        updateToSaveExpense.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToSaveExpense);

        assertThat(getSendMessage().getFirst().getText())
                .isEqualTo("Отправьте потраченную сумму");

        Message messageWithAmount = new Message();
        messageWithAmount.setChat(new Chat(1L, "test"));
        messageWithAmount.setText("100");
        updateToSaveExpense.setCallbackQuery(null);
        updateToSaveExpense.setMessage(messageWithAmount);

        updateHandler.handle(updateToSaveExpense);

        Mockito.verify(expenseService, Mockito.times(1)).getCategories(Mockito.any());
        SendMessage messageFromBot = getSendMessage().get(1);
        assertThat(messageFromBot.getText())
                .isEqualTo("Выберите категорию траты");
        InlineKeyboardMarkup replyMarkup = (InlineKeyboardMarkup) messageFromBot.getReplyMarkup();
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getText()).isEqualTo(category.getName());
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getCallbackData()).isEqualTo(category.getId().getId().toString());
        assertThat(replyMarkup.getKeyboard().get(1).getFirst().getText()).isEqualTo("Добавить категорию");
        assertThat(replyMarkup.getKeyboard().get(1).getFirst().getCallbackData()).isEqualTo(Step.CREATING_EXPENSE_CATEGORY.name());

        callbackQuery.setData(category.getId().getId().toString());
        updateToSaveExpense.setCallbackQuery(callbackQuery);
        updateToSaveExpense.setMessage(null);

        updateHandler.handle(updateToSaveExpense);

        assertThat(getSendMessage().get(2).getText())
                .isEqualTo("Введите описание траты");

        Message messageWithDescription = new Message();
        messageWithDescription.setFrom(new User(1L, "test", false));
        messageWithDescription.setChat(new Chat(1L, "test"));
        messageWithDescription.setText("testExpense");
        updateToSaveExpense.setCallbackQuery(null);
        updateToSaveExpense.setMessage(messageWithDescription);

        updateHandler.handle(updateToSaveExpense);

        Mockito.verify(expenseService, Mockito.times(1)).addSpending(Mockito.any(UserId.class), Mockito.any(ChatId.class), Mockito.any(UserSession.class));
        assertThat(getSendMessage().get(3).getText()).isEqualTo("Трата успешно сохранена");

        newSessionAfterEachGeneralMenu(updateHandler, () -> {
            try {
                return getSendMessage().get(4);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void saveLimitation() throws TelegramApiException, WrongFormat {
        Mockito.when(telegramClient.execute(Mockito.any(AnswerCallbackQuery.class))).thenReturn(null);

        UpdateHandler updateHandler = newHandler();

        Update updateToSaveLimitation = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(Step.ADDING_MONTH_LIMITATION.name());
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        callbackQuery.setMessage(message);
        callbackQuery.setId("testCallbackQueryId");
        updateToSaveLimitation.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToSaveLimitation);

        assertThat(getSendMessage().getFirst().getText())
                .isEqualTo("Отправьте сумму ограничения");

        Message messageWithLimitation = new Message();
        messageWithLimitation.setFrom(new User(1L, "test", false));
        messageWithLimitation.setChat(new Chat(1L, "test"));
        messageWithLimitation.setText("100");
        updateToSaveLimitation.setCallbackQuery(null);
        updateToSaveLimitation.setMessage(messageWithLimitation);

        updateHandler.handle(updateToSaveLimitation);

        Mockito.verify(expenseService, Mockito.times(1)).setOrUpdateLimitation(Mockito.any(UserId.class), Mockito.any(ChatId.class), Mockito.anyString());
        SendMessage messageFromBot = getSendMessage().get(1);
        assertThat(messageFromBot.getText())
                .isEqualTo("Ограничение успешно установлено");

        newSessionAfterEachGeneralMenu(updateHandler, () -> {
            try {
                return getSendMessage().get(2);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void saveStartDay() throws TelegramApiException, WrongFormat {
        Mockito.when(telegramClient.execute(Mockito.any(AnswerCallbackQuery.class))).thenReturn(null);

        UpdateHandler updateHandler = newHandler();

        Update updateToSaveStartDay = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(Step.INPUT_START_DAY.name());
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        callbackQuery.setMessage(message);
        callbackQuery.setId("testCallbackQueryId");
        updateToSaveStartDay.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToSaveStartDay);

        assertThat(getSendMessage().getFirst().getText())
                .isEqualTo("Отправьте день начала/окончания месяца");

        Message messageWithStartDay = new Message();
        messageWithStartDay.setFrom(new User(1L, "test", false));
        messageWithStartDay.setChat(new Chat(1L, "test"));
        messageWithStartDay.setText("11");
        updateToSaveStartDay.setCallbackQuery(null);
        updateToSaveStartDay.setMessage(messageWithStartDay);

        updateHandler.handle(updateToSaveStartDay);

        Mockito.verify(expenseService, Mockito.times(1)).saveInputStartDay(Mockito.any(UserId.class), Mockito.any(ChatId.class), Mockito.anyString());
        SendMessage messageFromBot = getSendMessage().get(1);
        assertThat(messageFromBot.getText())
                .isEqualTo("День успешно установлен");

        newSessionAfterEachGeneralMenu(updateHandler, () -> {
            try {
                return getSendMessage().get(2);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void checkStatus() throws TelegramApiException {
        Mockito.when(telegramClient.execute(Mockito.any(AnswerCallbackQuery.class))).thenReturn(null);
        Mockito.when(expenseService.getStatus(Mockito.any(ChatId.class), Mockito.any(UserId.class))).thenReturn(new SpendingStatus(new BigDecimal("100.0"), new BigDecimal("100.0"), Map.of("test1", new BigDecimal(1), "test2", new BigDecimal(2))));

        UpdateHandler updateHandler = newHandler();

        Update updateToGetStatus = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(Step.GETTING_CURRENT_STATUS.name());
        Message message = new Message();
        message.setChat(new Chat(1L, "test"));
        callbackQuery.setFrom(new User(1L, "test", false));
        callbackQuery.setMessage(message);
        callbackQuery.setId("testCallbackQueryId");
        updateToGetStatus.setCallbackQuery(callbackQuery);

        updateHandler.handle(updateToGetStatus);

        assertThat(getSendMessage().getFirst().getText())
                .isEqualTo("Месячное ограничение: 100,0 Потрачено на данный момент: 100,0, Остаток в этом месяце: 0,0\n" +
                        "test2: 2,0\n" +
                        "test1: 1,0\n");

        newSessionAfterEachGeneralMenu(updateHandler, () -> {
            try {
                return getSendMessage().get(1);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void newSessionAfterEachGeneralMenu(UpdateHandler updateHandler, Supplier<SendMessage> initMessageSup) throws TelegramApiException {
        updateHandler.handle(getNewMessage());
        SendMessage initMessage = initMessageSup.get();
        Assertions.assertThat(initMessage.getText()).isEqualTo("Выберите дальнейшее действие");
        InlineKeyboardMarkup replyMarkup = (InlineKeyboardMarkup) initMessage.getReplyMarkup();
        Map<String, String> kvGeneralMenu = replyMarkup.getKeyboard().stream().collect(Collectors.toMap($ -> $.getFirst().getCallbackData(), $ -> $.getFirst().getText()));
        assertThat(kvGeneralMenu.get(Step.SAVING_EXPENSE.name())).isEqualTo("Ввести одну трату");
        assertThat(kvGeneralMenu.get(Step.GETTING_CURRENT_STATUS.name())).isEqualTo("Текущий статус по тратам");
        assertThat(kvGeneralMenu.get(Step.ADDING_MONTH_LIMITATION.name())).isEqualTo("Добавить месячное ограничение");
        assertThat(kvGeneralMenu.get(Step.CREATING_EXPENSE_CATEGORY.name())).isEqualTo("Добавить категорию");
        assertThat(kvGeneralMenu.get(Step.DOWNLOAD_EXCEL_FILE.name())).isEqualTo("Получить траты в виде excel-файла");
    }

    private UpdateHandler newHandler() {
        return new UpdateHandler(telegramClient, expenseService, quickExpenseFlowService);
    }

    private Update getNewMessage() {
        Update update = new Update();
        Message message = new Message();
        message.setText("test");
        message.setChat(new Chat(1L, "test"));
        update.setCallbackQuery(null);
        update.setMessage(message);
        return update;
    }

    private List<SendMessage> getSendMessage() throws TelegramApiException {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);

        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        return captor.getAllValues();
    }

//проверить статус без ограничения -> статус
//проверить статус с ограничением -> статус
//скачивание файла
//поменять ограничение -> проверить статус
}
