package org.enerscope.probabilistic;

import java.util.function.DoubleSupplier;

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
    public DoubleSupplier sampler(long seed) {
        if (!Float.isFinite(value))
            throw new IllegalArgumentException("Finite constant required");
        return () -> value;
    }

    @Override
    public float generateValue() {
        return value;
    }
}
