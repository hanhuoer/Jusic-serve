package com.scoder.jusic.controller;

import com.scoder.jusic.common.message.Response;
import com.scoder.jusic.common.page.HulkPage;
import com.scoder.jusic.common.page.Page;
import com.scoder.jusic.configuration.JusicProperties;
import com.scoder.jusic.model.Chat;
import com.scoder.jusic.model.MessageType;
import com.scoder.jusic.model.Music;
import com.scoder.jusic.service.ConfigService;
import com.scoder.jusic.service.MusicService;
import com.scoder.jusic.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author H
 */
@Controller
@Slf4j
public class MusicController {

    @Autowired
    private JusicProperties jusicProperties;
    @Autowired
    private MusicService musicService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private ConfigService configService;
    private static final List<String> roles = new ArrayList<String>() {{
        add("root");
        add("admin");
    }};

    @MessageMapping("/music/pick")
    public void pick(Music music, StompHeaderAccessor accessor) {

        // 点歌消息反馈
        log.info("收到点歌请求: {}", music.getName());
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String nickName = sessionService.getNickName(sessionId);
        Chat chat = new Chat();
        chat.setContent("点歌 " + music.getName());
        chat.setNickName(nickName);
        chat.setSessionId(sessionId);
        sessionService.send(MessageType.CHAT, Response.success(chat));

        // 点歌结果反馈
        Music pick = musicService.getMusic(music.getName());
        boolean isNull = null == pick
                || null == pick.getId()
                || null == pick.getUrl()
                || null == pick.getDuration()
                || 0 > pick.getPrivilege().getSt()
                || 0 >= pick.getPrivilege().getFl();
        if (isNull) {
            log.info("点歌失败, 可能音乐不存在, 关键字: {}, 即将向客户端反馈点歌失败消息", music.getName());
            sessionService.send(MessageType.NOTICE, Response.failure((Object) null, "点歌失败"));
        } else if (musicService.isBlack(pick.getId())) {
            log.info("点歌失败, 音乐: {} 已被拉黑, 关键字: {}, 即将向客户端反馈点歌失败消息", pick.getId(), music.getName());
            sessionService.send(MessageType.NOTICE, Response.failure((Object) null, "点歌失败, 音乐已被拉黑"));
        } else if (musicService.isPicked(pick.getId())) {
            log.info("点歌失败, 音乐: {} 已在播放列表中, 关键字: {}, 即将向客户端反馈点歌失败消息", pick.getId(), music.getName());
            sessionService.send(MessageType.NOTICE, Response.failure((Object) null, "点歌失败, 已在播放列表"));
        } else {
            log.info("点歌成功, 音乐: {}, 时长: {}, 链接: {}, 即将向客户端广播消息以及列表", pick.getName(), pick.getDuration(), pick.getUrl());
            musicService.toPick(sessionId, pick);
            LinkedList<Music> pickList = musicService.getPickList();
            sessionService.send(MessageType.NOTICE, Response.success((Object) null, "点歌成功"));
            log.info("点歌成功");
            sessionService.send(MessageType.PICK, Response.success(pickList, "点歌列表"));
            log.info("向客户端发送点歌列表, 共 {} 首, 列表: {}", pickList.size(), pickList);
        }
    }

    @MessageMapping("/music/skip/vote")
    public void skip(StompHeaderAccessor accessor) {
        // 投票消息反馈
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String nickName = sessionService.getNickName(sessionId);
        Chat chat = new Chat();
        chat.setSessionId(sessionId);
        chat.setContent("投票切歌");
        chat.setNickName(nickName);
        sessionService.send(MessageType.CHAT, Response.success(chat));

        Long voteCount = 0L;
        Long vote = 0L;
        Long size = sessionService.size();
        if (roles.contains(sessionService.getRole(sessionId))) {
            // 管理员
            configService.setPushSwitch(true);
            log.info("推送开关开启, 原因: 投票通过 - 管理员参与投票");
            sessionService.send(MessageType.NOTICE, Response.success((Object) null, "切歌成功"));
        } else {
            // 投票
            vote = musicService.vote(sessionId);
            voteCount = musicService.getVoteCount();
            if (vote == 0) {
                sessionService.send(MessageType.NOTICE, Response.failure((Object) null, "你已经投过票了"));
                log.info("你已经投过票了");
            }
            sessionService.send(MessageType.NOTICE, Response.success((Object) null, voteCount + "/" + size + " 投票成功"));
        }
        log.info("投票成功");
        if (voteCount == 1 && vote != 0 && voteCount < size * jusicProperties.getVoteRate()) {
            sessionService.send(MessageType.NOTICE, Response.success((Object) null, "有人希望切歌, 如果支持请发送“投票切歌”"));
            log.info("有人希望切歌, 如果支持请发送“投票切歌”");
        }
    }

    /**
     * 调整音乐顺序
     *
     * @return 调整后的点歌列表
     */
    @MessageMapping("/music/order")
    @SendTo("/topic/music/order")
    public Response modifyPickOrder(LinkedList<Music> musicList, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            return Response.failure((Object) null, "你没有权限");
        }

        musicService.modifyPickOrder(musicList);
        return Response.success(musicList, "调整成功");
    }

    /**
     * 删除播放列表的音乐
     *
     * @param music    music
     * @param accessor accessor
     */
    @MessageMapping("/music/delete")
    public void deletePickMusic(Music music, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试删除音乐但没有权限, 已被阻止", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            musicService.deletePickMusic(music);
            LinkedList<Music> pickList = musicService.getPickList();
            log.info("session: {} 删除音乐: {} 已成功, 即将广播删除后的播放列表", sessionId, music.getName());
            sessionService.send(sessionId, MessageType.NOTICE, Response.success((Object) null, "删除成功"));
            sessionService.send(MessageType.PICK, Response.success(pickList, "删除后的播放列表"));
        }
    }

    /**
     * 置顶播放列表的音乐
     *
     * @param music    music
     * @param accessor accessor
     */
    @MessageMapping("/music/top")
    public void topPickMusic(Music music, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试置顶音乐但没有权限, 已被阻止", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            musicService.topPickMusic(music);
            LinkedList<Music> pickList = musicService.getPickList();
            log.info("session: {} 置顶音乐: {} 已成功, 即将广播置顶操作后的播放列表", sessionId, music.getName());
            sessionService.send(sessionId, MessageType.NOTICE, Response.success((Object) null, "置顶成功"));
            sessionService.send(MessageType.PICK, Response.success(pickList, "置顶后的播放列表"));
        }
    }

    @MessageMapping("/music/black")
    public void black(Music music, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试拉黑音乐: {}, 没有权限, 已被阻止", sessionId, music.getId());
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            Long black = musicService.black(music.getId());
            if (black > 0) {
                log.info("session: {} 尝试拉黑音乐: {} 已成功", sessionId, music.getName());
                sessionService.send(MessageType.NOTICE, Response.success((Object) null, "音乐拉黑成功"));
            } else {
                log.info("session: {} 尝试拉黑音乐: {} 已成功, 重复拉黑", sessionId, music.getName());
                sessionService.send(MessageType.NOTICE, Response.success((Object) null, "音乐重复拉黑"));
            }
        }
    }

    @MessageMapping("/music/unblack")
    public void unblack(Music music, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        String role = sessionService.getRole(sessionId);
        if (!roles.contains(role)) {
            log.info("session: {} 尝试置顶音乐但没有权限, 已被阻止", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "你没有权限"));
        } else {
            Long unblack = musicService.unblack(music.getId());
            if (unblack > 0) {
                log.info("session: {} 尝试漂白音乐: {} 已成功", sessionId, music.getName());
                sessionService.send(MessageType.NOTICE, Response.success((Object) null, "音乐漂白成功"));
            } else {
                log.info("session: {} 尝试漂白音乐: {} 漂白异常, 可能不在黑名单中", sessionId, music.getName());
                sessionService.send(MessageType.NOTICE, Response.success((Object) null, "音乐漂白异常, 可能不在黑名单中"));
            }
        }
    }

    @MessageMapping("/music/search")
    public void search(Music music, HulkPage hulkPage, StompHeaderAccessor accessor) {
        String sessionId = accessor.getHeader("simpSessionId").toString();
        if (Objects.isNull(music) || Objects.isNull(music.getName())) {
            log.info("session: {} 尝试搜索音乐, 但关键字为空", sessionId);
            sessionService.send(sessionId, MessageType.NOTICE, Response.failure((Object) null, "请输入要搜索的关键字"));
        }
        Page<List<Music>> page = musicService.search(music, hulkPage);
        log.info("session: {} 尝试搜索音乐, 关键字: {}, 即将向该用户推送结果", accessor.getHeader("simpSessionId"), music.getName());
        sessionService.send(sessionId, MessageType.SEARCH, Response.success(page, "搜索结果"));
    }

}
