package com.scoder.jusic.controller;

import com.scoder.jusic.common.message.Response;
import com.scoder.jusic.model.Chat;
import com.scoder.jusic.model.MessageType;
import com.scoder.jusic.model.User;
import com.scoder.jusic.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

/**
 * @author H
 */
@Controller
@Slf4j
public class ChatController {

    @Autowired
    private SessionService sessionService;
    private static final List<String> roles = new ArrayList<String>() {{
        add("root");
        add("admin");
    }};

    @MessageMapping("/chat")
    public void chat(Chat chat, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        User user = sessionService.getUser(sessionId);
        User black = sessionService.getBlack(sessionId);
        long currentTime = System.currentTimeMillis();
        if (null != black && black.getSessionId().equals(sessionId)) {
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你已被拉黑"));
        } else if (null != user.getLastMessageTime() && currentTime - user.getLastMessageTime() < 2000) {
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "发言时间间隔太短"));
        } else {
            chat.setSessionId(sessionId);
            chat.setNickName(user.getNickName());
            sessionService.send(MessageType.CHAT, Response.success(chat));
            sessionService.setLastMessageTime(user, currentTime);
        }
    }

    @MessageMapping("/chat/black")
    public void black(User user, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试拉黑用户: {}, 已被阻止", sessionId, user.getSessionId());
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            User black = sessionService.getUser(user.getSessionId());
            sessionService.black(black);
            log.info("session: {} 拉黑用户: {}, 已成功", sessionId, user.getSessionId());
            sessionService.send(sessionId, MessageType.NOTICE, Response.success((Object) null, "拉黑成功"));
        }
    }

    @MessageMapping("/chat/unblack")
    public void unblack(User user, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试解除黑名单: {}, 已被阻止", sessionId, user.getSessionId());
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            sessionService.unblack(user.getSessionId());
            log.info("session: {} 用户: {} 已被移除黑名单", sessionId, user.getSessionId());
            sessionService.send(sessionId, MessageType.NOTICE, Response.success((Object) null, "已移除黑名单"));
        }
    }

}
