package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 短链接。code 字段由发号器生成后 Base62 编码，
 * 通过 code 上的唯一索引实现短码到原始链接的快速映射。
 */
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
     * 点击次数。由定时任务从 Redis 累加器回写，
     * 跳转路径上不直接更新数据库，避免拖慢跳转。
     */
    private Long hitCount;

    /**
     * 是否启用：1 启用 2 停用。停用的短链拒绝跳转，
     * 用于下线违规链接而不删除记录。
     */
    private Integer enabled;

    /**
     * 过期时间，为空表示长期有效。运营活动链接通常都会带上。
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
