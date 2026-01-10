package com.cpallas.expenses.controller.consumer;

import com.cpallas.expenses.controller.process.ChatUpdateDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
public class UpdateConsumer implements LongPollingUpdateConsumer {
    private final ChatUpdateDispatcher dispatcher;

    public UpdateConsumer(ChatUpdateDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(dispatcher::submit);
    }
}
