package com.iodsky.sweldox.csvimport;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class LocalTimeCsvConverter extends AbstractBeanField<LocalTime, String> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("hh:mm a")
    };

    @Override
    protected LocalTime convert(String value) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
        if (value == null || value.isBlank()) return null;

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {

            }
        }

        throw new CsvDataTypeMismatchException("Unable to parse time: " + value);
    }
}
