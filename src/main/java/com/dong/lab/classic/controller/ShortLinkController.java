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

@RestController
@RequestMapping("/api/classic/short-link")
@RequiredArgsConstructor
@Tag(name = "classic-short-link")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping
    public Result<String> create(@RequestParam String url) {
        return Result.success(shortLinkService.create(url));
    }

    @GetMapping("/resolve")
    public Result<String> resolve(@RequestParam String code) {
        return Result.success(shortLinkService.resolve(code));
    }

    @GetMapping("/detail")
    public Result<ShortLinkResponse> detail(@RequestParam String code) {
        return Result.success(ShortLinkResponse.from(shortLinkService.findByCode(code)));
    }

    @GetMapping("/hits")
    public Result<Long> hits(@RequestParam String code) {
        return Result.success(shortLinkService.hitCount(code));
    }

    @GetMapping("/s/{code}")
    @Operation(summary = "302 redirect, permanent 301 would be cached by the browser and break hit counting")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String origin = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(origin)).build();
    }

}
