package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.ContinuousSampler;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
@Entity
@DiscriminatorValue("UNIFORM")
@NoArgsConstructor
public class UniformDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float min_val;
    @Column(name = "val_b")
    private float max_val;

    @Transient
    private ContinuousSampler sampler;

    public UniformDistributionCase(float min, float max) {
        this.min_val = min;
        this.max_val = max;
        initSampler();
    }

    @PostLoad
    private void initSampler() {
        UniformContinuousDistribution distribution = UniformContinuousDistribution.of(min_val, max_val);
        UniformRandomProvider rng = RandomSource.MWC_256.create();
        this.sampler = (ContinuousSampler) distribution.createSampler(rng);
    }

    @Override
    public float generateValue() {
        if (sampler == null) initSampler();
        return (float) sampler.sample();
    }
}
