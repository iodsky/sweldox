package com.iodsky.motorph.csvimport;

public record CsvResult<T, K>(T entity, K source) {}