package com.scoder.jusic.service;

import com.scoder.jusic.model.Music;

import java.util.LinkedList;

/**
 * @author H
 */
public interface MusicService {

    /**
     * 接收点歌请求，推送点歌信息
     *
     * @param sessionId session id
     * @param request   music info
     * @return music info
     */
    Music toPick(String sessionId, Music request);

    /**
     * 切歌
     *
     * @return 将要播放的音乐
     */
    Music musicSwitch();

    /**
     * get pick list
     *
     * @return linked list
     */
    LinkedList<Music> getPickList();

    /**
     * 修改点歌列表顺序
     *
     * @param musicList -
     * @return -
     */
    Long modifyPickOrder(LinkedList<Music> musicList);

    /**
     * 投票
     *
     * @param sessionId session id
     * @return 0：投票失败，已经参与过。1：投票成功
     */
    Long vote(String sessionId);

    /**
     * 从集合中获取参与投票的人数
     *
     * @return 参与投票的人数
     */
    Long getVoteCount();

    /**
     * get music
     *
     * @param keyword keyword
     * @return music
     */
    Music getMusic(String keyword);

    /**
     * get music url
     *
     * @param musicId music id
     * @return url
     */
    String getMusicUrl(String musicId);

    /**
     * 删除音乐
     *
     * @param music music
     */
    void deletePickMusic(Music music);

    /**
     * top pick music
     *
     * @param music -
     */
    void topPickMusic(Music music);

    /**
     * black
     *
     * @param id music id
     * @return -
     */
    Long black(String id);

    /**
     * un black
     *
     * @param id music id
     * @return -
     */
    Long unblack(String id);

    /**
     * is black?
     *
     * @param id music id
     * @return -
     */
    boolean isBlack(String id);

    /**
     * is picked ?
     *
     * @param id music id
     * @return
     */
    boolean isPicked(String id);
}
