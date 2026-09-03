package com.dong.lab.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortLink {

    /**
     * 主键
     */
    private Long id;

    /**
     * 短码，由发号器生成后 Base62 编码
     */
    private String code;

    /**
     * 原始链接
     */
    private String originUrl;

    /**
     * 点击次数
     */
    private Long hitCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
