package com.cpallas.expenses.admin;

public class AdminBroadcastUnauthorizedException extends RuntimeException {

    public AdminBroadcastUnauthorizedException() {
        super("Admin authorization is invalid.");
    }
}
