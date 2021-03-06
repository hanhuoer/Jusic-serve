package com.scoder.jusic.controller;

import com.scoder.jusic.common.message.Response;
import com.scoder.jusic.configuration.JusicProperties;
import com.scoder.jusic.model.MessageType;
import com.scoder.jusic.model.Playlist;
import com.scoder.jusic.service.ConfigService;
import com.scoder.jusic.service.MusicService;
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
public class PlaylistController {

    @Autowired
    private SessionService sessionService;
    @Autowired
    private MusicService musicService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private JusicProperties.RedisKeys redisKeys;
    private static final List<String> roles = new ArrayList<String>() {{
        add("root");
        add("admin");
    }};

    /**
     * 设置歌单列表
     *
     * @param playlist
     * @param accessor
     */
    @MessageMapping("/playlist/modify")
    public void playlistModify(Playlist playlist, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试修改播放列表但没有权限, 已被阻止", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            if (musicService.setMusicDefaultList(playlist.getId())) {
                sessionService.send(sessionId, MessageType.PLAYLIST, Response.success((Object) null, "已修改歌单为: " + playlist.getId()));
                log.info("session: {} 修改歌单为: {} 结果: 成功", sessionId, playlist.getId());
            } else {
                sessionService.send(sessionId, MessageType.PLAYLIST, Response.failure((Object) null, "修改歌单: " + playlist.getId() + " 失败"));
                log.info("session: {} 修改歌单为: {} 结果: 失败", sessionId, playlist.getId());
            }
        }
    }

    /**
     * 手动更新歌单列表
     *
     * @param accessor
     */
    @MessageMapping("/playlist/update")
    public void playlistUpdate(StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试手动刷新播放列表但没有权限, 已被阻止", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            String playlistId = (String) configService.get(redisKeys.getPlaylistIdCurrent());
            if (musicService.setMusicDefaultList(playlistId)) {
                sessionService.send(sessionId, MessageType.PLAYLIST, Response.success((Object) null, String.format("歌单: %s, 刷新成功", playlistId)));
                log.info("session: {} 刷新歌单: {} 结果: 成功", sessionId, playlistId);
            } else {
                sessionService.send(sessionId, MessageType.PLAYLIST, Response.failure((Object) null, String.format("歌单: %s, 刷新失败", playlistId)));
                log.info("session: {} 刷新歌单: {} 结果: 失败", sessionId, playlistId);
            }
        }
    }

}
