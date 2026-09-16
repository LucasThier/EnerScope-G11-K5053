package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.ContinuousSampler;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.NormalDistribution;

@Entity
@DiscriminatorValue("NORMAL")
@NoArgsConstructor
public class NormalDistributionCase extends ProbabilisticDistribution {
    @Column(name = "val_a")
    private float media;
    @Column(name = "val_b")
    private float constantDistribution; // Desviación estándar

    @Transient
    private ContinuousSampler sampler;

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
        this.sampler = (ContinuousSampler) distribution.createSampler(rng);
    }

    @Override
    public float generateValue() {
        if (sampler == null) initSampler(); // Safety check
        return (float) sampler.sample();
    }
}
