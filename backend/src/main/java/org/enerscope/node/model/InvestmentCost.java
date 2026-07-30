package org.enerscope.node.model;

import java.util.List;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCost extends BaseEntity {

    @OneToMany
    @JoinColumn(nullable = false)
    private List<InvestmentCostComponent> components;
}