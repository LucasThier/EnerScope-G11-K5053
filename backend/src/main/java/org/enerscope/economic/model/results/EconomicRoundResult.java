package org.enerscope.economic.model.results;
import java.util.List;
import org.enerscope.simulator.AnnualNodeMetrics;
public record EconomicRoundResult(int round,
                                  String seed,
                                  List<AnnualNodeMetrics> rawMetrics,
                                  EconomicResult result) {}
