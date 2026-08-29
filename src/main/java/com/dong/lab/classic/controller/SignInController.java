package com.dong.lab.classic.controller;

import com.dong.lab.classic.service.SignInService;
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
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/classic/sign")
@RequiredArgsConstructor
@Tag(name = "classic-sign")
public class SignInController {

    private final SignInService signInService;

    @PostMapping
    public Result<Boolean> signIn(@RequestParam String userId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.signIn(userId, date == null ? LocalDate.now() : date));
    }

    @GetMapping
    public Result<Boolean> hasSigned(@RequestParam String userId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.hasSigned(userId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/streak")
    public Result<Long> streak(@RequestParam String userId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.continuousDays(userId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/month")
    public Result<Long> monthCount(@RequestParam String userId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return Result.success(signInService.countInMonth(userId, month == null ? YearMonth.now() : month));
    }

    @GetMapping("/calendar")
    public Result<Map<String, Boolean>> calendar(@RequestParam String userId,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return Result.success(signInService.monthCalendar(userId, month == null ? YearMonth.now() : month));
    }

}
