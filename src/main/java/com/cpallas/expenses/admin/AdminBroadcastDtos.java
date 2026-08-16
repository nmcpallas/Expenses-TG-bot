package com.cpallas.expenses.admin;

import java.util.List;

public final class AdminBroadcastDtos {

    private AdminBroadcastDtos() {
    }

    public record Request(String text) {
    }

    public record Failure(long chatId, String error) {
    }

    public record Result(
            int totalChats,
            int sent,
            int failed,
            List<Failure> failures
    ) {
    }
}
