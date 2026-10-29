package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.Random;
import java.util.function.DoubleSupplier;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.NormalDistribution;

@lombok.Getter
@Entity
@DiscriminatorValue("NORMAL")
@NoArgsConstructor
public class NormalDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float media;
    @Column(name = "val_b")
    private float constantDistribution; // Desviación estándar

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
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

    @Override public DoubleSupplier sampler(long seed) {
        if (!Float.isFinite(media) || !Float.isFinite(constantDistribution) || constantDistribution <= 0)
            throw new IllegalArgumentException("Invalid distribution parameters");
        Random random = new Random(seed);
        return () -> media + constantDistribution * random.nextGaussian();
    }

    @Override
    public float generateValue() {
        if (sampler == null) initSampler(); // Safety check
        return (float) sampler.sample();
    }
}
