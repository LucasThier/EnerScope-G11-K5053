package org.enerscope.economic.model.results;

import org.enerscope.economic.model.enums.PaybackStatus;

public record PaybackResult(PaybackStatus status,
                            Integer period,
                            Integer year,
                            boolean becomesNegativeAgain) {}
