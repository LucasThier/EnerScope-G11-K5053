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
}