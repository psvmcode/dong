package com.dong.lab.classic.controller;

import com.dong.lab.classic.service.UniqueVisitorService;
import com.dong.lab.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/classic/uv")
@RequiredArgsConstructor
@Tag(name = "classic-uv")
public class UniqueVisitorController {

    private final UniqueVisitorService uniqueVisitorService;

    @PostMapping("/record")
    public Result<Long> record(@RequestParam(defaultValue = "home") String page,
                               @RequestParam String visitorId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(uniqueVisitorService.record(page, visitorId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam(defaultValue = "home") String page,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(uniqueVisitorService.count(page, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/range")
    public Result<Long> countBetween(@RequestParam(defaultValue = "home") String page,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(uniqueVisitorService.countBetween(page, from, to));
    }

}
