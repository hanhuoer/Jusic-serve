package com.scoder.jusic.model;

import lombok.*;

import java.io.Serializable;
import java.util.List;

/**
 * @author H
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Playlist extends Message implements Serializable {

    private static final long serialVersionUID = -5404741219684417455L;

    /**
     * 歌单 id
     */
    private String id;
    /**
     * 歌单名
     */
    private String name;
    /**
     * 歌单列表
     */
    private List tracks;

}
