package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProbabilisticDistributionDTO {
    public static ProbabilisticDistributionDTO from(org.enerscope.probabilistic.ProbabilisticDistribution value) {
        if (value == null) return null;
        if (value instanceof org.enerscope.probabilistic.ConstantValue c)
            return new ProbabilisticDistributionDTO("CONSTANT", c.getValue(), null);
        if (value instanceof org.enerscope.probabilistic.NormalDistributionCase n)
            return new ProbabilisticDistributionDTO("NORMAL", n.getMedia(), n.getConstantDistribution());
        if (value instanceof org.enerscope.probabilistic.UniformDistributionCase u)
            return new ProbabilisticDistributionDTO("UNIFORM", u.getMin_val(), u.getMax_val());
        throw new IllegalArgumentException("Unsupported distribution");
    }

    String type;
    Float val_a;
    Float val_b;

    public ProbabilisticDistributionDTO( String type, Float val_a, Float val_b){
        this.type = type;
        this.val_a = val_a;
        this.val_b = val_b;
    }
}
