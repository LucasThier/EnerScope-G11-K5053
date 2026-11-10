package org.enerscope.economic.dto;
import java.math.BigDecimal;
import org.enerscope.economic.model.results.EconomicIndicators;
public record EconomicRoundSummary(int round,
                                   String seed,
                                   BigDecimal npv,
                                   EconomicIndicators indicators) {}
