package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How node payloads from the editor bind. {@link BaseNodeDTO} picks its subtype
 * from {@code type.nodeType}, falling back to the fields present when the type
 * is missing, so these pin both the subtype chosen and the shapes a decline
 * curve may take.
 */
class NodeDTODeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE = "\"name\":\"N\",\"state\":\"PROPOSED\",\"lifespanInMonths\":240,"
            + "\"upkeepCosts\":0,\"operatingCosts\":0,\"maintenanceIntervalInDays\":365,\"maintenanceDuration\":48,"
            + "\"wastePercentage\":0,\"investmentCost\":{\"components\":[]},";

    @Test
    void aDeclineCurveObjectBindsWithItsTypeAndParameters() throws Exception {
        ProbabilisticDistributionDTO curve = mapper.readValue(
                "{\"type\":\"UNIFORM\",\"val_a\":1.5,\"val_b\":3}", ProbabilisticDistributionDTO.class);

        assertEquals("UNIFORM", curve.getType());
        assertEquals(1.5f, curve.getVal_a());
        assertEquals(3f, curve.getVal_b());
    }

    @Test
    void aBareNumberBindsAsAConstantDeclineCurve() throws Exception {
        assertEquals("CONSTANT", mapper.readValue("5", ProbabilisticDistributionDTO.class).getType());
        assertEquals(5f, mapper.readValue("5", ProbabilisticDistributionDTO.class).getVal_a());
        assertEquals(2.5f, mapper.readValue("2.5", ProbabilisticDistributionDTO.class).getVal_a());
    }

    @Test
    void aWellPayloadWithANumericDeclineCurveIsDeducedAsAWell() throws Exception {
        BaseNodeDTO node = mapper.readValue("{" + BASE + "\"maxCollectionCapacity\":100,\"declineCurve\":5,"
                + "\"gasRichness\":0.5,\"dtmTime\":24,\"dtmCost\":0,\"surface\":1}", BaseNodeDTO.class);

        WellDTO well = assertInstanceOf(WellDTO.class, node);
        assertEquals("CONSTANT", well.getDeclineCurve().getType());
        assertEquals(48, well.getMaintenanceDuration());
    }

    @Test
    void consumptionPayloadsAreDeducedToTheirOwnTypes() throws Exception {
        BaseNodeDTO internal = mapper.readValue("{" + BASE + "\"consumptionSummer\":1,\"consumptionAutumn\":2,"
                + "\"consumptionWinter\":3,\"consumptionSpring\":4}", BaseNodeDTO.class);
        BaseNodeDTO industrial = mapper.readValue("{" + BASE + "\"consumption\":7}", BaseNodeDTO.class);

        assertEquals(3f, assertInstanceOf(InternalConsumptionDTO.class, internal).getConsumptionWinter());
        assertEquals(7f, assertInstanceOf(IndustrialConsumptionDTO.class, industrial).getConsumption());
    }

    private static final String LIQUEFACTION = "\"maxProcessingCapacity\":800,\"mtpaRatio\":90,"
            + "\"intermediateStorage\":20000,\"gasConsumption\":8";

    @Test
    void aGroundLiquefactionPlantIsNotMistakenForAnFLNGUnit() throws Exception {
        // Its fields are a subset of an FLNG unit's: deduction by fields matched
        // both and every create from the editor failed as a malformed body.
        BaseNodeDTO node = mapper.readValue("{" + BASE + "\"type\":{\"vertical\":\"LIQUEFACTION\","
                + "\"role\":\"INTERMEDIATE\",\"nodeType\":\"GROUND_LIQUEFACTION_PLANT\"}," + LIQUEFACTION + "}",
                BaseNodeDTO.class);

        GroundBasedLiquefactionPlantDTO plant = assertInstanceOf(GroundBasedLiquefactionPlantDTO.class, node);
        assertEquals(90f, plant.getMTPARatio());
    }

    @Test
    void anFLNGUnitIsReadAsOneWithItsShipCapacity() throws Exception {
        BaseNodeDTO node = mapper.readValue("{" + BASE + "\"type\":{\"vertical\":\"LIQUEFACTION\","
                + "\"role\":\"INTERMEDIATE\",\"nodeType\":\"FLNG_UNIT\"}," + LIQUEFACTION
                + ",\"vesselDepth\":20,\"hiringCost\":0,\"shipCapacity\":2}", BaseNodeDTO.class);

        assertEquals(2, assertInstanceOf(FLNGUnitDTO.class, node).getShipCapacity());
    }

    @Test
    void withoutANodeTypeTheMostSpecificMatchWins() throws Exception {
        BaseNodeDTO node = mapper.readValue("{" + BASE + LIQUEFACTION + "}", BaseNodeDTO.class);

        assertInstanceOf(GroundBasedLiquefactionPlantDTO.class, node);
    }

    @Test
    void anUnknownNodeTypeIsRejected() {
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(
                "{" + BASE + "\"type\":{\"nodeType\":\"VOLCANO\"},\"consumption\":1}", BaseNodeDTO.class));
    }

    @Test
    void aSubtypeStillBindsDirectly() throws Exception {
        WellDTO well = mapper.readValue("{\"name\":\"Pozo\",\"maxCollectionCapacity\":10}", WellDTO.class);

        assertEquals(10f, well.getMaxCollectionCapacity());
    }
}
