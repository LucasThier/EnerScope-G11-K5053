package org.enerscope.economic.model.configuration;

import java.math.BigDecimal;

public record Ownership(String taxEntityId,
                        BigDecimal share) {}
