package com.dong.lab.classic.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public interface SignInService {

    boolean signIn(String userId, LocalDate date);

    boolean hasSigned(String userId, LocalDate date);

    long countInMonth(String userId, YearMonth month);

    long continuousDays(String userId, LocalDate today);

    Map<String, Boolean> monthCalendar(String userId, YearMonth month);

}
