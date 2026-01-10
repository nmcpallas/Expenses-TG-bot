package com.cpallas.expenses.controller.process;

import com.cpallas.expenses.controller.handler.ChatNotifier;
import com.cpallas.expenses.controller.handler.UpdateHandler;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class ChatUpdateDispatcher implements AutoCloseable {

    private final Map<Long, ChatQueue> updatesByChat;
    private final Map<Long, BlockInfo> blockedChats;
    private final ExecutorService vtExecutor;
    private final UpdateHandler updateHandler;
    private final ChatNotifier chatNotifier;

    private final int queueCapacity;
    private final int blockDuration;


    public ChatUpdateDispatcher(int queueCapacity, int blockDuration, UpdateHandler updateHandler, ChatNotifier chatNotifier) {
        this.queueCapacity = queueCapacity;
        this.blockDuration = blockDuration;
        this.chatNotifier = chatNotifier;
        this.updatesByChat = new ConcurrentHashMap<>();
        this.blockedChats = new ConcurrentHashMap<>();
        this.vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.updateHandler = updateHandler;
    }

    public void submit(Update update) {
        Long chatId = extractChatId(update);
        if (chatId == null) {
            vtExecutor.submit(() -> safeHandle(update));
            return;
        }
        if (isBlocked(chatId)) {
            return;
        }

        ChatQueue chatQueue = updatesByChat.computeIfAbsent(chatId, id -> new ChatQueue());
        boolean offered = chatQueue.tryEnqueue(update);
        if (!offered) {
            blockChat(chatId);
            handleBlockedChat(chatId);
        }
    }

    private boolean isBlocked(Long chatId) {
        BlockInfo blockInfo = blockedChats.get(chatId);
        if (blockInfo == null) return false;

        Instant now = Instant.now();
        if (now.isAfter(blockInfo.blockedUntil())) {
            blockedChats.remove(chatId);
            return false;
        }
        return true;
    }

    private void blockChat(Long chatId) {
        Instant blockedUntil = Instant.now().plusSeconds(blockDuration);
        blockedChats.put(chatId, new BlockInfo(blockedUntil, false));
    }

    private void handleBlockedChat(Long chatId) {
        BlockInfo info = blockedChats.get(chatId);
        if (info == null) return;

        if (!info.notified()) {
            blockedChats.put(chatId, info.withNotified(true));
            vtExecutor.submit(() -> {
                try {
                    chatNotifier.notifyBlocked(chatId, info.blockedUntil());
                } catch (Exception e) {
                    log.error("Error notifying blocked chat", e);
                }
            });
        }
    }

    private void safeHandle(Update update) {
        try {
            updateHandler.handle(update);
        } catch (Exception e) {
            log.error("Error handling update", e);
        }
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }

    @PreDestroy
    @Override
    public void close() throws InterruptedException {
        for (ChatQueue queue : updatesByChat.values()) {
            queue.stop();
        }
        vtExecutor.shutdown();
        vtExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private class ChatQueue {
        private final BlockingQueue<Update> queue;
        private final Future<?> worker;
        private volatile boolean running = true;

        private ChatQueue() {
            this.queue = new ArrayBlockingQueue<>(queueCapacity);
            this.worker = vtExecutor.submit(this::loop);
        }

        private void loop() {
            while (running || !queue.isEmpty()) {
                try {
                    Update update = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (update == null) continue;

                    safeHandle(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        boolean tryEnqueue(Update update) {
            try {
                return queue.offer(update, 100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        void stop() {
            running = false;
            worker.cancel(true);
        }
    }

    private record BlockInfo(Instant blockedUntil, boolean notified) {

        BlockInfo withNotified(boolean notified) {
            return new BlockInfo(this.blockedUntil, notified);
        }
    }
}
