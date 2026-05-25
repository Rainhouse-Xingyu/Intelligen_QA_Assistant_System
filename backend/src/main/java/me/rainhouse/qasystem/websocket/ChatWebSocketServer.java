package me.rainhouse.qasystem.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@ServerEndpoint("/ws/chat/{role}/{userId}")
@Component
public class ChatWebSocketServer {

    // 存放在线的用户连接 (key: userId)
    private static final ConcurrentHashMap<String, Session> userSessionPool = new ConcurrentHashMap<>();
    
    // 存放在线的客服连接 (key: adminId)
    private static final ConcurrentHashMap<String, Session> adminSessionPool = new ConcurrentHashMap<>();

    // 存放在线的客服ID，用于派单逻辑
    private static final CopyOnWriteArraySet<String> onlineAdmins = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("role") String role, @PathParam("userId") String userId) {
        if ("admin".equals(role)) {
            adminSessionPool.put(userId, session);
            onlineAdmins.add(userId);
            log.info("客服 [{}] 上线，当前在线客服数: {}", userId, onlineAdmins.size());
        } else {
            userSessionPool.put(userId, session);
            log.info("用户 [{}] 上线连接 WebSocket", userId);
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("role") String role, @PathParam("userId") String userId) {
        if ("admin".equals(role)) {
            adminSessionPool.remove(userId);
            onlineAdmins.remove(userId);
            log.info("客服 [{}] 下线", userId);
        } else {
            userSessionPool.remove(userId);
            log.info("用户 [{}] 断开 WebSocket", userId);
        }
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("role") String role, @PathParam("userId") String userId) {
        log.info("收到来自 {} [{}] 的消息: {}", role, userId, message);
        // 通常消息是通过 HTTP 接口发送的，WebSocket 主要用于系统的即时下行推送
        // 但如果前端通过 WebSocket 互发消息，也可在此处解析并分发。
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误", error);
    }

    /**
     * 服务端主动推送消息给特定用户
     */
    public static void sendMessageToUser(String userId, String message) {
        Session session = userSessionPool.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("推送消息给用户 [{}] 失败", userId, e);
            }
        }
    }

    /**
     * 服务端主动推送消息给特定客服
     */
    public static void sendMessageToAdmin(String adminId, String message) {
        Session session = adminSessionPool.get(adminId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("推送消息给客服 [{}] 失败", adminId, e);
            }
        }
    }

    /**
     * 获取一个空闲的客服ID（简单的随机或轮询逻辑）
     */
    public static String getAvailableAdmin() {
        if (onlineAdmins.isEmpty()) {
            return null;
        }
        // 简单随机拿一个在线客服跑单
        return onlineAdmins.iterator().next();
    }
}