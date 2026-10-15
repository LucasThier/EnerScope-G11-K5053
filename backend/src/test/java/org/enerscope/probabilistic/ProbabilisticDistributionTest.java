package org.enerscope.probabilistic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProbabilisticDistributionTest {

    @Test
    void aUniformDistributionSamplesWithinItsBounds() {
        // Creating one used to throw a ClassCastException (wrong sampler type).
        UniformDistributionCase uniform = new UniformDistributionCase(2f, 4f);

        for (int i = 0; i < 1000; i++) {
            float value = uniform.generateValue();
            assertTrue(value >= 2f && value <= 4f, "out of bounds: " + value);
        }
    }

    @Test
    void aNormalDistributionSamplesAroundItsMean() {
        NormalDistributionCase normal = new NormalDistributionCase(10f, 1f);

        double sum = 0;
        for (int i = 0; i < 2000; i++) {
            sum += normal.generateValue();
        }
        assertEquals(10.0, sum / 2000, 0.2);
    }

    @Test
    void eachKindReportsItsTypeAndParameters() {
        assertEquals("CONSTANT", new ConstantValue(3f).distributionType());
        assertEquals(3f, new ConstantValue(3f).firstParameter());
        assertEquals(0f, new ConstantValue(3f).secondParameter());

        UniformDistributionCase uniform = new UniformDistributionCase(1f, 5f);
        assertEquals("UNIFORM", uniform.distributionType());
        assertEquals(1f, uniform.firstParameter());
        assertEquals(5f, uniform.secondParameter());

        NormalDistributionCase normal = new NormalDistributionCase(8f, 2f);
        assertEquals("NORMAL", normal.distributionType());
        assertEquals(8f, normal.firstParameter());
        assertEquals(2f, normal.secondParameter());
    }

    @Test
    void anIndependentCopyKeepsTheParametersInANewInstance() {
        for (ProbabilisticDistribution original : new ProbabilisticDistribution[]{
                new ConstantValue(3f), new UniformDistributionCase(1f, 5f), new NormalDistributionCase(8f, 2f)}) {
            ProbabilisticDistribution copy = original.independentCopy();

            assertNotSame(original, copy);
            assertEquals(original.getClass(), copy.getClass());
            assertEquals(original.firstParameter(), copy.firstParameter());
            assertEquals(original.secondParameter(), copy.secondParameter());
            assertNull(copy.getId(), "a copy is never a second handle on the stored row");
        }
    }
}
