package com.dinehub.websocket;

import com.dinehub.service.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTicketHandshakeInterceptorTest {

    private final WebSocketTicketService ticketService = mock(WebSocketTicketService.class);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);
    private AdminTicketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminTicketHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "ticketService", ticketService);
    }

    @Test
    void validTicketBindsEmployeeIdentity() {
        String ticket = "12345678-1234-1234-1234-123456789012";
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/order?ticket=" + ticket));
        when(ticketService.consume(ticket)).thenReturn(9L);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertEquals(9L, attributes.get(WebSocketServer.EMPLOYEE_ID_ATTRIBUTE));
    }

    @Test
    void invalidTicketRejectsHandshake() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/order?ticket=bad"));
        when(ticketService.consume("bad")).thenReturn(null);

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
