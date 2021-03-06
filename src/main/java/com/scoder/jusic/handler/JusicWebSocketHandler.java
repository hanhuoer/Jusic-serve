package com.scoder.jusic.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * https://blog.csdn.net/qq_32331073/article/details/84444412
 * <p>
 * 配合 Spring 提供的 WebSocketHandlerDecoratorFactory 在不改变代码的情况下
 * 为 SubProtocolWebSocketHandler 增加功能
 *
 * @author H
 */
@Component
@Slf4j
public class JusicWebSocketHandler extends WebSocketHandlerDecorator {

    @Autowired
    private JusicWebSocketHandlerAsync jusicWebSocketHandlerAsync;

    public JusicWebSocketHandler(WebSocketHandler delegate) {
        super(delegate);
    }

    /**
     * WebSocket 握手成功并且连接状态已经打开之后被调用
     * </p>
     * 此阶段可以向所有会话广播当前在线人数，下面要做的就是这个功能
     *
     * @param session session
     * @throws Exception -
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        jusicWebSocketHandlerAsync.afterConnectionEstablished(session);
        super.afterConnectionEstablished(session);
    }

    /**
     * 在 WebSocket 任一方关闭连接之后或者发生传输错误的时候调用，
     * 此阶段会话可能仍然处于打开状态，
     * 因此不建议此阶段发消息
     *
     * @param session     session
     * @param closeStatus -
     * @throws Exception -
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        jusicWebSocketHandlerAsync.afterConnectionClosed(session, closeStatus);
        super.afterConnectionClosed(session, closeStatus);
    }

}
