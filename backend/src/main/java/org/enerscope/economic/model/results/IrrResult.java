package org.enerscope.economic.model.results;

import java.math.BigDecimal;
import org.enerscope.economic.model.enums.IrrStatus;

public record IrrResult(IrrStatus status, BigDecimal rate) {}
