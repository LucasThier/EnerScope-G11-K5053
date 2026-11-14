package org.enerscope.node.model.enums;

public enum NodeTypeEnum {
    WELL(VerticalEnum.EXTRACTION),
    GATHERING_NETWORK(VerticalEnum.EXTRACTION),
    TREATMENT_PLANT(VerticalEnum.EXTRACTION),
    PIPELINE(VerticalEnum.TRANSPORTATION),
    PIPELINE_CONECTION(VerticalEnum.TRANSPORTATION),
    COMPRESSING_PLANT(VerticalEnum.TRANSPORTATION),
    GROUND_LIQUEFACTION_PLANT(VerticalEnum.LIQUEFACTION),
    FLNG_UNIT(VerticalEnum.LIQUEFACTION),
    SEAPORT_TERMINAL(VerticalEnum.EXPORT),
    LNG_CAMER(VerticalEnum.EXPORT),
    INTERNAL_CONSUMPTION(VerticalEnum.INTERNAL_CONSUMPTION),
    INDUSTRIAL_CONSUMPTION(VerticalEnum.INTERNAL_CONSUMPTION);

    private final VerticalEnum vertical;

    NodeTypeEnum(VerticalEnum vertical) {
        this.vertical = vertical;
    }

    public VerticalEnum vertical() {
        return vertical;
    }

    public VerticalEnum checkedVertical(VerticalEnum declared) {
        if (declared != null && declared != vertical) {
            throw new IllegalArgumentException("Node vertical does not match its type: " + name()
                    + " belongs to " + vertical + ", not " + declared);
        }
        return vertical;
    }
}
