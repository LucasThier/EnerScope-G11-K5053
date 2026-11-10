package org.enerscope.economic.model.configuration;

import java.util.UUID;

/** A null seller allocates revenue using the delivery node's ownership. */
public record CommercialContract(String id,
                                UUID deliveryNodeId,
                                EconomicRule revenueRule) {}
