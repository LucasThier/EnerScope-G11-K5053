package org.enerscope.comparison.dto;

import java.math.BigDecimal;

/** Physical totals of one node over the whole horizon, in raw simulator units (mean over rounds if probabilistic). */
public record NodeFlowDTO(BigDecimal input,
                          BigDecimal output,
                          BigDecimal exported,
                          BigDecimal losses,
                          BigDecimal operatingHours,
                          BigDecimal events) {}
