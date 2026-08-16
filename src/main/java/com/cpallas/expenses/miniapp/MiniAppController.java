package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mini-app")
@RequiredArgsConstructor
@Slf4j
public class MiniAppController {

    private final TelegramMiniAppAuthService authService;
    private final MiniAppService miniAppService;

    @GetMapping("/dashboard")
    public MiniAppDtos.Dashboard dashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return miniAppService.dashboard(authService.authenticate(authorization));
    }

    @GetMapping("/expenses")
    public List<MiniAppDtos.Expense> expenses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return miniAppService.expenses(authService.authenticate(authorization), limit);
    }

    @PatchMapping("/expenses/{id}")
    public MiniAppDtos.Expense updateExpense(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            @RequestBody MiniAppDtos.UpdateExpense request
    ) {
        return miniAppService.updateExpense(
                authService.authenticate(authorization),
                new ExpenseId(id),
                request
        );
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id
    ) {
        miniAppService.deleteExpense(
                authService.authenticate(authorization),
                new ExpenseId(id)
        );
    }

    @GetMapping("/categories")
    public List<MiniAppDtos.Category> categories(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return miniAppService.categories(authService.authenticate(authorization));
    }

    @PatchMapping("/categories/{id}")
    public MiniAppDtos.Category updateCategory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            @RequestBody MiniAppDtos.UpdateCategory request
    ) {
        return miniAppService.updateCategory(
                authService.authenticate(authorization),
                new CategoryId(id),
                request
        );
    }

    @DeleteMapping("/categories/{id}")
    public MiniAppDtos.Settings deleteCategory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id
    ) {
        return miniAppService.deleteCategory(
                authService.authenticate(authorization),
                new CategoryId(id)
        );
    }

    @GetMapping("/analytics")
    public MiniAppDtos.Analytics analytics(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "month") String period
    ) {
        return miniAppService.analytics(authService.authenticate(authorization), period);
    }

    @GetMapping("/settings")
    public MiniAppDtos.Settings settings(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return miniAppService.settings(authService.authenticate(authorization));
    }

    @PatchMapping("/settings")
    public MiniAppDtos.Settings updateSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MiniAppDtos.UpdateSettings request
    ) {
        return miniAppService.updateSettings(
                authService.authenticate(authorization),
                request
        );
    }

    @ExceptionHandler(MiniAppUnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(MiniAppUnauthorizedException exception) {
        log.warn("Mini App authorization rejected: reason={}", exception.getMessage());
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
