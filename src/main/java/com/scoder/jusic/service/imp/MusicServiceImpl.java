package com.scoder.jusic.service.imp;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.scoder.jusic.common.page.HulkPage;
import com.scoder.jusic.configuration.JusicProperties;
import com.scoder.jusic.model.Music;
import com.scoder.jusic.repository.*;
import com.scoder.jusic.service.MusicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
        music.setSessionId(sessionId);
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
            String musicUrl = this.getMusicUrl(result.getId());
            if (Objects.nonNull(musicUrl)) {
                result.setUrl(musicUrl);
                log.info("音乐链接已超时, 已更新链接");
            } else {
                log.info("音乐链接更新失败, 接下来客户端音乐链接可能会失效, 请检查音乐服务");
            }
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
    public String getMusicUrl(String musicId) {
        HttpResponse<String> response = null;
        String result = null;

        Integer failCount = 0;

        while (failCount < jusicProperties.getRetryCount()) {
            try {
                response = Unirest.get(jusicProperties.getMusicServeDomain() + "/netease/song/" + musicId + "/url")
                        .asString();

                if (response.getStatus() != 200) {
                    failCount++;
                } else {
                    JSONObject jsonObject = JSONObject.parseObject(response.getBody());
                    log.info("获取音乐链接结果：{}, response: {}", jsonObject.get("message"), jsonObject);
                    if (jsonObject.get("code").equals(1)) {
                        result = JSONObject.parseObject(jsonObject.get("data").toString()).get("url").toString();
                        break;
                    }
                }
            } catch (UnirestException e) {
                failCount++;
                log.error("音乐链接获取异常, 请检查音乐服务; UnirestException: [{}]", e.getMessage());
            }
        }

        return result;
    }

    @Override
    public void deletePickMusic(Music music) {
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        for (int i = 0; i < pickMusicList.size(); i++) {
            if (music.getId().equals(pickMusicList.get(i).getId())) {
                pickMusicList.remove(pickMusicList.get(i));
                break;
            }
        }
        musicPickRepository.reset();
        musicPickRepository.rightPushAll(pickMusicList.toArray());
    }

    @Override
    public void topPickMusic(Music music) {
        List<Music> newPickMusicList = new LinkedList<>();
        List<Music> pickMusicList = musicPickRepository.getPickMusicList();
        for (int i = 0; i < pickMusicList.size(); i++) {
            if (music.getId().equals(pickMusicList.get(i).getId())) {
                newPickMusicList.add(pickMusicList.get(i));
                pickMusicList.remove(pickMusicList.get(i));
                break;
            }
        }
        pickMusicList.addAll(newPickMusicList);
        musicPickRepository.reset();
        musicPickRepository.rightPushAll(pickMusicList.toArray());
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
        return playing.getId().equals(id);
    }

    @Override
    public HulkPage search(Music music, HulkPage hulkPage) {
        StringBuilder url = new StringBuilder()
                .append(jusicProperties.getMusicServeDomain())
                .append("/netease/songs/")
                .append(music.getName())
                .append("/search")
                .append("/").append(hulkPage.getPageIndex() - 1)
                .append("/").append(hulkPage.getPageSize());
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(url.toString())
                    .asString();
            JSONObject responseJsonObject = JSONObject.parseObject(response.getBody());
            if (responseJsonObject.getInteger("code") == 1) {
                JSONArray data = responseJsonObject.getJSONObject("data").getJSONArray("data");
                Integer count = responseJsonObject.getJSONObject("data").getInteger("count");
                List list = JSONObject.parseObject(JSONObject.toJSONString(data), List.class);
                hulkPage.setData(list);
                hulkPage.setTotalSize(count);
            } else {
                log.info("音乐搜索接口异常, 请检查音乐服务");
            }
        } catch (UnirestException e) {
            log.error("音乐搜索接口异常, 请检查音乐服务; UnirestException: [{}]", e.getMessage());
        }
        return hulkPage;
    }

    @Override
    public List<String> getPlaylistSongs(Integer playlistId) {
        StringBuilder url = new StringBuilder()
                .append(jusicProperties.getMusicServeDomain())
                .append("/netease/playlist")
                .append("/").append(playlistId)
                .append("/songs");

        HttpResponse<String> response = null;
        List<String> result = new ArrayList<>();

        try {
            response = Unirest.get(url.toString())
                    .asString();
            JSONObject responseJsonObject = JSONObject.parseObject(response.getBody());
            if (responseJsonObject.getInteger("code") == 1) {
                JSONObject data = responseJsonObject.getJSONObject("data");
                JSONArray tracks = data.getJSONArray("tracks");
                for (Object track : tracks) {
                    JSONObject t = (JSONObject) track;
                    result.add(t.getString("id"));
                }
            } else {
                log.info("歌单列表接口异常, 请检查音乐服务");
            }
        } catch (UnirestException e) {
            log.error("歌单列表接口异常, 请检查音乐服务; UnirestException: [{}]", e.getMessage());
        }
        return result;
    }

}
