package com.iodsky.sweldox.csvimport;

import com.opencsv.bean.AbstractBeanField;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateCsvConverter extends AbstractBeanField<LocalDate, String> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    @Override
    protected LocalDate convert(String value) {
        if (value == null || value.isBlank()) return null;
        for (var formatter : FORMATTERS) {
            try {
                    return LocalDate.parse(value.trim(), formatter);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("An error has occurred while parsing date: " + value);
    }

}
