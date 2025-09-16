package com.performance.excel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.excel.dto.DownloadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ProgressWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
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
        String webSocketSessionId = userIdToWebSocketSessionMapping.get(userId);
        if (webSocketSessionId != null) {
            WebSocketSession session = sessions.get(webSocketSessionId);
            if (session != null && session.isOpen()) {
                try {
                    String message = objectMapper.writeValueAsString(progress);
                    synchronized (session) {
                        session.sendMessage(new TextMessage(message));
                    }
                    log.debug("Progress sent to session {}: {}%", userId, progress.getProgressPercentage());
                } catch (IOException e) {
                    log.error("Failed to send progress to session: {}", userId, e);
                    sessions.remove(webSocketSessionId);
                    userIdToWebSocketSessionMapping.remove(userId);
                } catch (Exception e) {
                    log.error("WebSocket state error for session {}: {}", userId, e.getMessage());
                    // WebSocket 상태 오류 발생 시 메시지 미전송
//                    broadcastProgressSafe(progress);
                }
            }
        } else {
            log.warn("No WebSocket session found for userId: {} (connected sessions: {})",
                    userId, sessions.size());
            
            // 대안: 안전한 브로드캐스트
            log.info("Falling back to broadcast for requestId: {}", progress.getRequestId());
            broadcastProgressSafe(progress);
        }
    }
    
    /**
     * 모든 활성 세션에 메시지 브로드캐스트 (안전한 버전)
     */
    public synchronized void broadcastProgressSafe(DownloadProgress progress) {
        sessions.values().parallelStream()
                .filter(WebSocketSession::isOpen)
                .forEach(session -> {
                    try {
                        synchronized (session) {
                            if (session.isOpen()) {
                                String message = objectMapper.writeValueAsString(progress);
                                session.sendMessage(new TextMessage(message));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to broadcast to session: {}", session.getId(), e);
                        sessions.remove(session.getId());
                    }
                });
    }
    
    /**
     * 모든 활성 세션에 메시지 브로드캐스트 (기존 버전 - 호환성용)
     */
    public void broadcastProgress(DownloadProgress progress) {
        broadcastProgressSafe(progress);
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
    
    /**
     * 활성 세션 수 조회
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
