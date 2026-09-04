package com.dong.lab.classic.controller;

import com.dong.lab.classic.dto.ShortLinkResponse;
import com.dong.lab.classic.service.ShortLinkService;
import com.dong.lab.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
/**
 * 短链接。短码由发号器生成后做 Base62 编码，
 * 因此同一个原始链接每次生成的短码都不同，这是刻意设计，避免被批量遍历。
 */
@RestController
@RequestMapping("/api/classic/short-link")
@RequiredArgsConstructor
@Tag(name = "经典场景-短链接")

public class ShortLinkController {

    /**
     * 短链接服务。
     */
    private final ShortLinkService shortLinkService;

    /**
     * 生成短链。
     */
    @PostMapping
    @Operation(summary = "生成短链，返回短码")
    public Result<String> create(@RequestParam String url) {
        return Result.success(shortLinkService.create(url));
    }

    /**
     * 解析短码得到原始地址，并累加点击数。
     */
    @GetMapping("/resolve")
    @Operation(summary = "解析短码为原始地址，并累加点击数")
    public Result<String> resolve(@RequestParam String code) {
        return Result.success(shortLinkService.resolve(code));
    }

    /**
     * 查询短链详情。
     */
    @GetMapping("/detail")
    @Operation(summary = "查询短链详情")
    public Result<ShortLinkResponse> detail(@RequestParam String code) {
        return Result.success(ShortLinkResponse.from(shortLinkService.findByCode(code)));
    }

    /**
     * 查询点击次数。
     */
    @GetMapping("/hits")
    @Operation(summary = "查询短链被点击的次数")
    public Result<Long> hits(@RequestParam String code) {
        return Result.success(shortLinkService.hitCount(code));
    }

    /**
     * 短链跳转。这里必须用 302 而不是 301：
     * 301 是永久重定向，会被浏览器缓存，之后再访问就不再经过服务端，
     * 点击统计会彻底失效。
     */
    @GetMapping("/s/{code}")
    @Operation(summary = "短链跳转，用 302 而非 301，否则点击统计会失效")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String origin = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(origin)).build();
    }

}
