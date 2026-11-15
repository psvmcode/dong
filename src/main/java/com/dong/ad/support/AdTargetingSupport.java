package com.dong.ad.support;

import com.dong.ad.dto.AdTargeting;
import com.dong.ad.dto.AdUnitUpdateRequest;
import com.dong.ad.dto.DeliveryRequest;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 定向条件工具。四个维度之间是与关系，单个维度内是或关系，
 * 空列表表示该维度不限——这是投放系统里最容易写反的一条规则。
 *
 * <p>比较统一转小写：地域与设备来自不同媒体，大小写不一致时
 * 严格相等会让广告莫名其妙地一条都投不出去。
 */
@Slf4j
@Component
public class AdTargetingSupport {

    /**
     * 把定向条件序列化成 JSON。null 列表归一成空数组，避免落库出现 null 字段。
     *
     * @param regions  地域
     * @param tags     人群标签
     * @param devices  设备
     * @param keywords 关键词
     * @return 定向 JSON
     */
    public String toJson(List<String> regions, List<String> tags, List<String> devices, List<String> keywords) {
        validate(regions);
        validate(tags);
        validate(devices);
        validate(keywords);
        return JsonUtils.toJson(new AdTargeting(safe(regions), safe(tags), safe(devices), safe(keywords)));
    }

    /**
     * 按更新请求合并定向条件。传 null 表示保持不变，传空数组表示清空限制。
     *
     * @param current 当前定向 JSON
     * @param request 更新请求
     * @return 合并后的定向 JSON
     */
    public String merge(String current, AdUnitUpdateRequest request) {
        AdTargeting existing = parse(current);
        List<String> regions = request.getRegions() == null ? existing.regions() : request.getRegions();
        List<String> tags = request.getTags() == null ? existing.tags() : request.getTags();
        List<String> devices = request.getDevices() == null ? existing.devices() : request.getDevices();
        List<String> keywords = request.getKeywords() == null ? existing.keywords() : request.getKeywords();
        return toJson(regions, tags, devices, keywords);
    }

    /**
     * 判断请求上下文是否命中定向条件。脏 JSON 一律判不命中，
     * 宁可少投也不能把定向失效的广告投给不该看到的人。
     *
     * @param targeting 定向 JSON
     * @param request   投放请求
     * @return 是否命中
     */
    public boolean matches(String targeting, DeliveryRequest request) {
        AdTargeting condition = parse(targeting);
        if (!dimensionMatches(condition.regions(), request.getRegion())) {
            return false;
        }
        if (!dimensionMatches(condition.devices(), request.getDevice())) {
            return false;
        }
        if (!intersects(condition.tags(), request.getTags())) {
            return false;
        }
        return intersects(condition.keywords(), request.getKeywords());
    }

    /**
     * 解析定向 JSON。写脏时抛业务异常而不是当成「不限」：
     * 把一条定向失效的广告投给不该看到的人，代价远大于一次召回失败。
     *
     * @param json 定向 JSON
     * @return 定向条件
     */
    private AdTargeting parse(String json) {
        if (json == null || json.isBlank()) {
            return AdTargeting.any();
        }
        try {
            return JsonUtils.fromJson(json, AdTargeting.class).normalized();
        } catch (Exception ex) {
            log.warn("invalid ad targeting json={}", json);
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "invalid targeting config");
        }
    }

    /**
     * 单值维度匹配，空条件表示不限。
     *
     * @param allowed 允许的取值
     * @param actual  请求取值
     * @return 是否匹配
     */
    private boolean dimensionMatches(List<String> allowed, String actual) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        String value = actual.toLowerCase(Locale.ROOT);
        return allowed.stream().limit(Constants.MAX_TARGETING_ITEMS)
                .anyMatch(item -> item != null && item.toLowerCase(Locale.ROOT).equals(value));
    }

    /**
     * 多值维度匹配，空条件表示不限，否则至少命中一个。
     *
     * @param allowed 允许的取值
     * @param actual  请求取值
     * @return 是否匹配
     */
    private boolean intersects(List<String> allowed, List<String> actual) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        List<String> normalized = actual.stream().limit(Constants.MAX_TARGETING_ITEMS)
                .filter(item -> item != null).map(item -> item.toLowerCase(Locale.ROOT)).toList();
        return allowed.stream().limit(Constants.MAX_TARGETING_ITEMS)
                .anyMatch(item -> item != null && normalized.contains(item.toLowerCase(Locale.ROOT)));
    }

    /**
     * 校验定向取值长度，防止单个超长字符串把 JSON 撑爆。
     *
     * @param values 定向取值
     */
    private void validate(List<String> values) {
        if (values == null) {
            return;
        }
        if (values.size() > Constants.MAX_TARGETING_ITEMS) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "too many targeting values");
        }
        for (String value : values) {
            if (value == null || value.length() > Constants.MAX_NAME_LENGTH) {
                throw new BusinessException(Constants.CODE_PARAM_INVALID, "invalid targeting value");
            }
        }
    }

    /**
     * null 列表转空列表，并复制一份可变副本，避免调用方后续修改影响已落库的 JSON。
     *
     * @param values 原始列表
     * @return 安全副本
     */
    private List<String> safe(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

}
