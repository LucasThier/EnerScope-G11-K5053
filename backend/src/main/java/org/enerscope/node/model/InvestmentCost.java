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
import org.enerscope.money.MoneyAmount;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCost extends BaseEntity {

    @OneToMany
    @JoinColumn(nullable = false)
    private List<InvestmentCostComponent> components;

    public MoneyAmount CalculateCost(BaseNode baseNode){
        return MoneyAmount.of(0).addAll(components.stream().map(component -> component.CalculateCost(baseNode)).toList());
    }
}