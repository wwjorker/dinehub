package com.dinehub.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已认证管理端的订单事件通道。
 */
@Component
@Slf4j
public class WebSocketServer extends TextWebSocketHandler {

    public static final String EMPLOYEE_ID_ATTRIBUTE = "employeeId";

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long employeeId = (Long) session.getAttributes().get(EMPLOYEE_ID_ATTRIBUTE);
        if (employeeId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing authenticated identity"));
            return;
        }

        WebSocketSession previous = sessions.put(employeeId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            previous.close(CloseStatus.NORMAL.withReason("replaced by a newer connection"));
        }
        log.info("admin order channel connected, employeeId={}", employeeId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long employeeId = (Long) session.getAttributes().get(EMPLOYEE_ID_ATTRIBUTE);
        if (employeeId != null) {
            sessions.remove(employeeId, session);
            log.info("admin order channel disconnected, employeeId={}", employeeId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("admin order channel transport error, session={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * 订单事件只广播给通过一次性 ticket 建立的管理端连接。
     */
    public void sendToAllClient(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.forEach((employeeId, session) -> {
            if (!session.isOpen()) {
                sessions.remove(employeeId, session);
                return;
            }
            try {
                // StandardWebSocketSession 不保证并发 sendMessage 安全。
                synchronized (session) {
                    session.sendMessage(textMessage);
                }
            } catch (Exception e) {
                sessions.remove(employeeId, session);
                log.warn("failed to push order event, employeeId={}", employeeId, e);
            }
        });
    }
}
