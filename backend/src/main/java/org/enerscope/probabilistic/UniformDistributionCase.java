package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
@Entity
@DiscriminatorValue("UNIFORM")
@NoArgsConstructor
public class UniformDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float min_val;
    @Column(name = "val_b")
    private float max_val;

    // commons-statistics' own sampler type. It used to be declared as commons-rng's
    // ContinuousSampler and cast to it, which failed at runtime: createSampler
    // returns a ContinuousDistribution.Sampler, so no distribution of this kind
    // could ever be created or loaded.
    @Transient
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
    public float generateValue() {
        if (sampler == null) initSampler();
        return (float) sampler.sample();
    }

    @Override
    public String distributionType() {
        return "UNIFORM";
    }

    @Override
    public float firstParameter() {
        return min_val;
    }

    @Override
    public float secondParameter() {
        return max_val;
    }

    @Override
    public ProbabilisticDistribution independentCopy() {
        return new UniformDistributionCase(min_val, max_val);
    }
}
