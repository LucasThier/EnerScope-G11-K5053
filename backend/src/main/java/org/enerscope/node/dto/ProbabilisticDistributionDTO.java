package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProbabilisticDistributionDTO {
    String type;
    Float val_a;
    Float val_b;

    public ProbabilisticDistributionDTO( String type, Float val_a, Float val_b){
        this.type = type;
        this.val_a = val_a;
        this.val_b = val_b;
    }
}
