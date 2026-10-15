package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.NormalDistribution;

@Entity
@DiscriminatorValue("NORMAL")
@NoArgsConstructor
public class NormalDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float media;
    @Column(name = "val_b")
    private float constantDistribution; // Desviación estándar

    // commons-statistics' own sampler type. It used to be declared as commons-rng's
    // ContinuousSampler and cast to it, which failed at runtime: createSampler
    // returns a ContinuousDistribution.Sampler, so no distribution of this kind
    // could ever be created or loaded.
    @Transient
    private ContinuousDistribution.Sampler sampler;

    public NormalDistributionCase(float media, float constantDistribution) {
        this.media = media;
        this.constantDistribution = constantDistribution;
        initSampler();
    }

    // Se ejecuta automáticamente al cargar la entidad desde la base de datos
    @PostLoad
    private void initSampler() {
        NormalDistribution distribution = NormalDistribution.of(media, constantDistribution);
        UniformRandomProvider rng = RandomSource.MWC_256.create();
        this.sampler = distribution.createSampler(rng);
    }

    @Override
    public float generateValue() {
        if (sampler == null) initSampler(); // Safety check
        return (float) sampler.sample();
    }

    @Override
    public String distributionType() {
        return "NORMAL";
    }

    @Override
    public float firstParameter() {
        return media;
    }

    @Override
    public float secondParameter() {
        return constantDistribution;
    }

    @Override
    public ProbabilisticDistribution independentCopy() {
        return new NormalDistributionCase(media, constantDistribution);
    }
}
