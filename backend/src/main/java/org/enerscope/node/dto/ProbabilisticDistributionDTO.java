package org.enerscope.node.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A probability distribution as the API carries it: {@code type} is
 * {@code CONSTANT} ({@code val_a} = the value), {@code UNIFORM} ({@code val_a} =
 * minimum, {@code val_b} = maximum) or {@code NORMAL} ({@code val_a} = mean,
 * {@code val_b} = standard deviation).
 *
 * <p>A bare number is accepted too and read as a constant, so a client that
 * sends {@code "declineCurve": 5} gets a 5 %/year constant decline instead of a
 * "malformed body" — which is what the editor's first version of the well form
 * ran into.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ProbabilisticDistributionDTO {
    String type;
    Float val_a;
    Float val_b;

    public ProbabilisticDistributionDTO( String type, Float val_a, Float val_b){
        this.type = type;
        this.val_a = val_a;
        this.val_b = val_b;
    }

    /** A number in place of the object: a constant distribution of that value. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProbabilisticDistributionDTO constant(double value) {
        return new ProbabilisticDistributionDTO("CONSTANT", (float) value, null);
    }
}
