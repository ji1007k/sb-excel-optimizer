package com.performance.excel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.excel.dto.DownloadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgressWebSocketHandler extends TextWebSocketHandler
        implements MessageListener {
    
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> stringRedisTemplate;    // excel 다운로드 진행률 저장용
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 사용자 ID -> 웹소켓 세션 ID 매핑
    private final ConcurrentHashMap<String, String> userIdToWebSocketSessionMapping = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String webSocketSessionId = session.getId();
        sessions.put(webSocketSessionId, session);
        
        // URL 쿼리 파라미터에서 사용자 ID 추출
        String userId = extractUserId(session);
        log.info("WebSocket connection established - WS ID: {}, USER ID: {}",
            webSocketSessionId, userId);
        
        if (userId != null) {
            userIdToWebSocketSessionMapping.put(userId, webSocketSessionId);
            log.info("Session mapping created: USER={} -> WS={}", userId, webSocketSessionId);
        } else {
            log.warn("Could not extract USER ID from URI: {}", session.getUri());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String webSocketSessionId = session.getId();
        sessions.remove(webSocketSessionId);
        
        // 역방향 매핑도 제거
        userIdToWebSocketSessionMapping.entrySet().removeIf(entry ->
            entry.getValue().equals(webSocketSessionId));
        
        log.info("WebSocket connection closed: {}", webSocketSessionId);
    }
    
    /**
     * 특정 세션에 진행률 전송 - 사용자 ID 기반
     * WebSocket 동시성 문제 해결을 위한 동기화 처리
     */
    public synchronized void sendProgress(String userId, DownloadProgress progress) {
        try {
            String message = objectMapper.writeValueAsString(progress);
            stringRedisTemplate.convertAndSend("excel:progress", userId + ":" + message);

            log.debug("Progress sent to session {}: {}%", userId, progress.getProgressPercentage());
        } catch (IOException e) {
            log.error("Failed to send progress to session: {}", userId, e);
        } catch (Exception e) {
            log.error("WebSocket state error for session {}: {}", userId, e.getMessage());
        }
    }

    // TODO 메시지 포맷 검증
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msg = new String(message.getBody());
            String[] parts = msg.split(":", 2);
            String userId = parts[0];
            String progressJson = parts[1];

            sendToLocalSession(userId, progressJson);
        } catch (Exception e) {
            log.error("Failed to handle message", e);
        }
    }

    private synchronized void sendToLocalSession(String userId, String message) {
        String wsId = userIdToWebSocketSessionMapping.get(userId);
        if (wsId != null) {
            WebSocketSession session = sessions.get(wsId);
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(message));
                    }
                    log.debug("진행률 전송 성공: userId={}", userId);
                } catch (Exception e) {
                    log.error("Send failed", e);
                    // 세션 정리
                    sessions.remove(wsId);
                    userIdToWebSocketSessionMapping.remove(userId);
                }
            } else {
                log.warn("WebSocket 세션 닫힘: userId={}", userId);
            }
        } else {
            log.debug("해당 서버에 userId={} 세션 없음 (다른 서버에 있음)", userId);
        }
    }
    
    /**
     * URL 쿼리 파라미터에서 사용자 ID 추출
     */
    private String extractUserId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String[] queryParts = query.split("&");
                for (String part : queryParts) {
                    if (part.startsWith("userId=")) {
                        String userId = part.substring("userId=".length());
                        return URLDecoder.decode(userId, StandardCharsets.UTF_8);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting User ID from WebSocket session", e);
            return null;
        }
    }

}
