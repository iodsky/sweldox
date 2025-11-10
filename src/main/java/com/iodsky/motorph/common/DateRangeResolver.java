package com.iodsky.motorph.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateRangeResolver {

    public DateRange resolve(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(15);
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }
        return new DateRange(startDate, endDate);
    }

}
