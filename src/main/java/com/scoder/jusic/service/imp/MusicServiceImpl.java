package com.scoder.jusic.service.imp;

import com.alibaba.fastjson.JSONObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.scoder.jusic.configuration.JusicProperties;
import com.scoder.jusic.model.Music;
import com.scoder.jusic.repository.*;
import com.scoder.jusic.service.MusicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author H
 */
@Service
@Slf4j
public class MusicServiceImpl implements MusicService {

    @Autowired
    private JusicProperties jusicProperties;
    @Autowired
    private MusicPickRepository musicPickRepository;
    @Autowired
    private MusicDefaultRepository musicDefaultRepository;
    @Autowired
    private MusicPlayingRepository musicPlayingRepository;
    @Autowired
    private MusicVoteRepository musicVoteRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private MusicBlackRepository musicBlackRepository;


    /**
     * 把音乐放进点歌列表
     */
    @Override
    public Music toPick(String sessionId, Music music) {
        music.setPickTime(System.currentTimeMillis());
        music.setNickName(sessionRepository.getSession(sessionId).getNickName());
        musicPickRepository.leftPush(music);
        log.info("点歌成功, 音乐: {}, 已放入点歌列表", music.getName());
        return music;
    }

    /**
     * 音乐切换
     *
     * @return -
     */
    @Override
    public Music musicSwitch() {
        Music result = null;
        if (musicPickRepository.size() < 1) {
            String keyword = musicDefaultRepository.randomMember();
            log.info("选歌列表为空, 已从默认列表中随机选择一首: {}", keyword);
            result = this.getMusic(keyword);
            result.setPickTime(System.currentTimeMillis());
            result.setNickName("system");
            musicPickRepository.leftPush(result);
        }

        result = musicPlayingRepository.pickToPlaying();
        // 防止选歌的时间超过音乐链接的有效时长
        if (result.getPickTime() + jusicProperties.getMusicExpireTime() <= System.currentTimeMillis()) {
            Music music = this.getMusic(result.getId());
            musicPlayingRepository.leftPush(music);
            log.info("音乐链接已超时, 已更新链接");
        }

        musicPlayingRepository.keepTheOne();

        return result;
    }

    /**
     * 获取点歌列表
     *
     * @return linked list
     */
    @Override
    public LinkedList<Music> getPickList() {
        LinkedList<Music> result = new LinkedList<>();
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        Music playing = musicPlayingRepository.getPlaying();
        Collections.reverse(pickMusicList);
        result.add(playing);
        result.addAll(pickMusicList);

        result.forEach(m -> {
            // 由于歌词数据量太大了, 而且列表这种不需要关注歌词, 具体歌词放到推送音乐的时候再给提供
            m.setLyric("");
        });
        return result;
    }

    @Override
    public Long modifyPickOrder(LinkedList<Music> musicList) {
        musicPickRepository.reset();
        return musicPickRepository.leftPushAll(musicList);
    }

    /**
     * 投票
     *
     * @return 失败 = 0, 成功 >= 1
     */
    @Override
    public Long vote(String sessionId) {
        return musicVoteRepository.add(sessionId);
    }

    /**
     * 从 redis set 中获取参与投票的人数
     *
     * @return 参与投票人数
     */
    @Override
    public Long getVoteCount() {
        return musicVoteRepository.size();
    }

    /**
     * 获取音乐
     * </p>
     * 外链, 歌词, 艺人, 专辑, 专辑图片, 时长
     *
     * @param keyword 音乐关键字 | 网易云音乐 id
     * @return 音乐信息
     */
    @Override
    public Music getMusic(String keyword) {
        HttpResponse<String> response = null;
        Music music = null;

        Integer failCount = 0;

        while (failCount < jusicProperties.getRetryCount()) {
            try {
                response = Unirest.get(jusicProperties.getMusicServeDomain() + "/netease/song/" + keyword)
                        .asString();

                if (response.getStatus() != 200) {
                    failCount++;
                } else {
                    JSONObject jsonObject = JSONObject.parseObject(response.getBody());
                    log.info("获取音乐结果：{}, response: {}", jsonObject.get("message"), jsonObject);
                    if (jsonObject.get("code").equals(1)) {
                        music = JSONObject.parseObject(jsonObject.get("data").toString(), Music.class);
                        break;
                    }
                }
            } catch (UnirestException e) {
                failCount++;
                log.error("音乐获取异常, 请检查音乐服务; UnirestException: [{}]", e.getMessage());
            }
        }

        return music;
    }

    @Override
    public void deletePickMusic(Music music) {
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        for (int i = 0; i < pickMusicList.size(); i++) {
            if (music.getName().equals(pickMusicList.get(i).getName())) {
                pickMusicList.remove(pickMusicList.get(i));
            }
        }
        musicPickRepository.reset();
        musicPickRepository.rightPushAll(pickMusicList.toArray());
    }

    @Override
    public void topPickMusic(Music music) {
        LinkedList<Music> newPickMusicList = new LinkedList<>();
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        for (int i = 0; i < pickMusicList.size(); i++) {
            if (music.getName().equals(pickMusicList.get(i).getName())) {
                newPickMusicList.add(pickMusicList.get(i));
                pickMusicList.remove(pickMusicList.get(i));
            }
        }
        newPickMusicList.addAll(pickMusicList);
        musicPickRepository.reset();
        musicPickRepository.leftPushAll(newPickMusicList.toArray());
    }

    @Override
    public Long black(String id) {
        return musicBlackRepository.add(id);
    }

    @Override
    public Long unblack(String id) {
        return musicBlackRepository.remove(id);
    }

    @Override
    public boolean isBlack(String id) {
        return musicBlackRepository.isMember(id);
    }

    @Override
    public boolean isPicked(String id) {
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        for (Music music : pickMusicList) {
            if (music.getId().equals(id)) {
                return true;
            }
        }
        Music playing = musicPlayingRepository.getPlaying();
        if (playing.getId().equals(id)) {
            return true;
        }
        return false;
    }

}
