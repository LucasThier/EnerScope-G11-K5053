package org.enerscope.economic.model.results;

public record SimulationMetadata(int rounds,
                                 String seed,
                                 String algorithm,
                                 int calculationVersion) {}
