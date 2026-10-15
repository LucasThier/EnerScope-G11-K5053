package org.enerscope.probabilistic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "probabilistic_distribution")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "distribution_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
public abstract class ProbabilisticDistribution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    public abstract float generateValue();

    /** The kind the API names this distribution with: {@code CONSTANT}, {@code UNIFORM} or {@code NORMAL}. */
    public abstract String distributionType();

    /** First parameter: the constant value, the uniform minimum or the normal mean. */
    public abstract float firstParameter();

    /** Second parameter: the uniform maximum or the normal standard deviation; 0 for a constant. */
    public abstract float secondParameter();

    /**
     * A new, unsaved distribution with the same parameters and its own random
     * generator. The simulator runs its Monte Carlo rounds on separate threads,
     * and the samplers behind {@link #generateValue()} are not thread-safe, so
     * every round draws from its own copy instead of sharing the entity's.
     */
    public abstract ProbabilisticDistribution independentCopy();
}