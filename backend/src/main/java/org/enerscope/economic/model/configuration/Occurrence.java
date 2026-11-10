package org.enerscope.economic.model.configuration;

import java.time.LocalDate;

public record Occurrence(LocalDate recognitionDate,
                         LocalDate cashDate) {}
