package org.enerscope.node.model;

import java.util.List;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.CascadeType;
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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(nullable = false)
    private List<InvestmentCostComponent> components;

    public MoneyAmount CalculateCost(BaseNode baseNode){
        return MoneyAmount.of(0).addAll(components.stream().map(component -> TryCalculateCost(component, baseNode)).toList());
    }
    private MoneyAmount TryCalculateCost(InvestmentCostComponent investmentCostComponent, BaseNode baseNode){
        if(components != null && !components.isEmpty()){
            try {
                return investmentCostComponent.CalculateCost(baseNode);
            } catch (RuntimeException e) {
                System.out.println("Catched error: " + e.getMessage());
                return MoneyAmount.of(0);
            }
        } else {
            throw new RuntimeException("Investment Cost components is empty");
        }
    }
}
