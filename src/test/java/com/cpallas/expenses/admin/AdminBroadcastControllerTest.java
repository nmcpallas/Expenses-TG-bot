package com.cpallas.expenses.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminBroadcastControllerTest {

    @Mock
    private AdminBroadcastAuthService authService;
    @Mock
    private AdminBroadcastService broadcastService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminBroadcastController(authService, broadcastService))
                .build();
    }

    @Test
    void broadcastsWhenAdminTokenIsValid() throws Exception {
        when(broadcastService.broadcast("Технические работы"))
                .thenReturn(new AdminBroadcastDtos.Result(3, 3, 0, List.of()));

        mockMvc.perform(post("/api/admin/broadcast")
                        .header("X-Admin-Token", "secret-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Технические работы"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChats").value(3))
                .andExpect(jsonPath("$.sent").value(3))
                .andExpect(jsonPath("$.failed").value(0));

        verify(authService).requireAuthorized("secret-token");
        verify(broadcastService).broadcast("Технические работы");
    }

    @Test
    void returnsUnauthorizedWhenAdminTokenIsInvalid() throws Exception {
        doThrow(new AdminBroadcastUnauthorizedException())
                .when(authService)
                .requireAuthorized("wrong-token");

        mockMvc.perform(post("/api/admin/broadcast")
                        .header("X-Admin-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Технические работы"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Admin authorization is invalid."));
    }
}
