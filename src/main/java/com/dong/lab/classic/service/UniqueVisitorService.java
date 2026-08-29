package com.dong.lab.classic.service;

import java.time.LocalDate;

public interface UniqueVisitorService {

    long record(String page, String visitorId, LocalDate date);

    long count(String page, LocalDate date);

    long countBetween(String page, LocalDate from, LocalDate to);

}
