package com.scoder.jusic.model;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author H
 */
@Data
@ToString
public class Privilege implements Serializable {

    private static final long serialVersionUID = -5505745559684417455L;

    private Long id;
    private Integer fee;
    private Integer payed;
    private Integer st;
    private Integer pl;
    private Integer dl;
    private Integer sp;
    private Integer cp;
    private Integer subp;
    private Boolean cs;
    private Integer maxbr;
    private Integer fl;
    private Boolean toast;
    private Integer flag;

}
