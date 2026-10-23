package org.enerscope.probabilistic;

import java.util.function.DoubleSupplier;
import java.util.Random;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;

@lombok.Getter
@Entity
@DiscriminatorValue("UNIFORM")
@NoArgsConstructor
public class UniformDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float min_val;
    @Column(name = "val_b")
    private float max_val;

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ContinuousDistribution.Sampler sampler;

    public UniformDistributionCase(float min, float max) {
        this.min_val = min;
        this.max_val = max;
        initSampler();
    }

    @PostLoad
    private void initSampler() {
        UniformContinuousDistribution distribution = UniformContinuousDistribution.of(min_val, max_val);
        UniformRandomProvider rng = RandomSource.MWC_256.create();
        this.sampler = distribution.createSampler(rng);
    }

    @Override
    public DoubleSupplier sampler(long seed) {
        if (!Float.isFinite(min_val) || !Float.isFinite(max_val) || min_val >= max_val)
            throw new IllegalArgumentException("Invalid distribution parameters");
        Random random = new Random(seed);
        return () -> min_val + (max_val - (double) min_val) * random.nextDouble();
    }

    @Override
    public float generateValue() {
        if (sampler == null) initSampler();
        return (float) sampler.sample();
    }
}
