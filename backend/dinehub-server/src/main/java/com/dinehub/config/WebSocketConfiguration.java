package com.dinehub.config;

import com.dinehub.websocket.AdminTicketHandshakeInterceptor;
import com.dinehub.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 管理端来单提醒 WebSocket 配置。
 * 连接只能使用管理员接口签发的一次性 ticket，长期 JWT 不进入 URL。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private AdminTicketHandshakeInterceptor ticketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketServer, "/ws/order")
                .addInterceptors(ticketHandshakeInterceptor);
    }
}
