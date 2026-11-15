package com.dong.ad.dto;

import java.util.List;

/**
 * 定向条件值对象。空列表表示「这一维不限」，四个维度之间是「与」关系。
 *
 * <p>用 record 是因为它一经创建就不该被改：召回过程中若被某处偷偷改了集合，
 * 同一个请求里前后两个单元会看到不同的定向条件，问题极难复现。
 *
 * @param regions  地域列表
 * @param tags     人群标签列表
 * @param devices  设备列表
 * @param keywords 关键词列表
 */
public record AdTargeting(List<String> regions, List<String> tags, List<String> devices, List<String> keywords) {

    /**
     * 创建全不限的定向条件。
     *
     * @return 空的定向条件
     */
    public static AdTargeting any() {
        return new AdTargeting(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 把 null 列表归一为空列表，避免落库 JSON 里出现 null 引发反序列化歧义。
     *
     * @return 归一化后的定向条件
     */
    public AdTargeting normalized() {
        return new AdTargeting(regions == null ? List.of() : regions,
                tags == null ? List.of() : tags,
                devices == null ? List.of() : devices,
                keywords == null ? List.of() : keywords);
    }

}
