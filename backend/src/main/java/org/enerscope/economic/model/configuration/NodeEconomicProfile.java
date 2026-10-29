package org.enerscope.economic.model.configuration;

import java.util.List;
import java.util.UUID;
import org.enerscope.economic.model.configuration.Ownership;
import org.enerscope.economic.model.configuration.EconomicRule;

public record NodeEconomicProfile(UUID nodeId,
                                  List<Ownership> ownership,
                                  List<EconomicRule> rules){}
