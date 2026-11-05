package org.enerscope.economic.model.results;

import java.math.BigDecimal;

public record PendingBalance(String sourceId,
                             String taxEntityId,
                             String kind,
                             BigDecimal amount,
                             java.time.LocalDate dueDate) {}
