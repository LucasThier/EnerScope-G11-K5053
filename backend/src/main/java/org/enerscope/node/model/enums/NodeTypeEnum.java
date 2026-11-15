package org.enerscope.node.model.enums;

public enum NodeTypeEnum {
    WELL(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR),
    GATHERING_NETWORK(VerticalEnum.EXTRACTION, StructuralRoleEnum.INTERMEDIATE),
    TREATMENT_PLANT(VerticalEnum.EXTRACTION, StructuralRoleEnum.INTERMEDIATE),
    PIPELINE(VerticalEnum.TRANSPORTATION, StructuralRoleEnum.INTERMEDIATE),
    PIPELINE_CONECTION(VerticalEnum.TRANSPORTATION, StructuralRoleEnum.INTERMEDIATE),
    COMPRESSING_PLANT(VerticalEnum.TRANSPORTATION, StructuralRoleEnum.INTERMEDIATE),
    GROUND_LIQUEFACTION_PLANT(VerticalEnum.LIQUEFACTION, StructuralRoleEnum.INTERMEDIATE),
    FLNG_UNIT(VerticalEnum.LIQUEFACTION, StructuralRoleEnum.INTERMEDIATE),
    SEAPORT_TERMINAL(VerticalEnum.EXPORT, StructuralRoleEnum.SINK),
    LNG_CAMER(VerticalEnum.EXPORT, StructuralRoleEnum.SINK),
    INTERNAL_CONSUMPTION(VerticalEnum.INTERNAL_CONSUMPTION, StructuralRoleEnum.SINK),
    INDUSTRIAL_CONSUMPTION(VerticalEnum.INTERNAL_CONSUMPTION, StructuralRoleEnum.SINK);

    private final VerticalEnum vertical;
    private final StructuralRoleEnum role;

    NodeTypeEnum(VerticalEnum vertical, StructuralRoleEnum role) {
        this.vertical = vertical;
        this.role = role;
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

    /** Structural role implied by the type; the server derives it, as it does the vertical. */
    public StructuralRoleEnum role() {
        return role;
    }

    /**
     * Returns the role of this type. A declared role that differs is rejected
     * (mapped to 400); a missing one is simply derived.
     */
    public StructuralRoleEnum checkedRole(StructuralRoleEnum declared) {
        if (declared != null && declared != role) {
            throw new IllegalArgumentException("Node role does not match its type: " + name()
                    + " is " + role + ", not " + declared);
        }
        return role;
    }
}
