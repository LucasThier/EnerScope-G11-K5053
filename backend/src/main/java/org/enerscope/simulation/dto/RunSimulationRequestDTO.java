package org.enerscope.simulation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Parameters of a simulation run. {@code rounds} is optional and defaults to
 * {@code Simulator.DEFAULT_ROUNDS}. Both are capped because the run happens
 * inside the request: the work grows with {@code years × rounds × nodes}.
 */
public record RunSimulationRequestDTO(
        @NotNull
        @Min(1)
        @Max(MAX_YEARS)
        Integer years,

        @Min(1)
        @Max(MAX_ROUNDS)
        Integer rounds
) {
    public static final int MAX_YEARS = 40;
    public static final int MAX_ROUNDS = 50;
}
