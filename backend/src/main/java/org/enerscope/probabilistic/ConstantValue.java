package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("CONSTANT")
@NoArgsConstructor
@AllArgsConstructor
public class ConstantValue extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float value;

    @Override
    public float generateValue() {
        return value;
    }

    @Override
    public String distributionType() {
        return "CONSTANT";
    }

    @Override
    public float firstParameter() {
        return value;
    }

    @Override
    public float secondParameter() {
        return 0f;
    }

    @Override
    public ProbabilisticDistribution independentCopy() {
        return new ConstantValue(value);
    }
}
