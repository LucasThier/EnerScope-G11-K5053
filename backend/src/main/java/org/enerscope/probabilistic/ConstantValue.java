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
}
