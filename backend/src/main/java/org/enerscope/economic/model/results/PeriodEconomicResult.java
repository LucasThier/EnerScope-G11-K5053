package org.enerscope.economic.model.results;

import java.math.BigDecimal;

/* Contiene el resultado de un periodo dentro del calculo del VAN*/
public record PeriodEconomicResult(int period,
                                   int year,
                                   BigDecimal taxableIncome,         // Ingresos Afectos a Impuestos (IAI)
                                   BigDecimal deductibleExpenses,    // Egresos Afectos a Impuestos (EAI)
                                   BigDecimal nonCashExpenses,       // Gastos No Deseembolsables (GND)
                                   BigDecimal resultBeforeTax,       // Resultados Antes de Impuestos (RAI)
                                   BigDecimal taxes,                 // Resultado Total de Impuestos
                                   BigDecimal resultAfterTax,        // Resultado Despues de Impuestos (RDI)
                                   BigDecimal nonTaxableIncome,      // Ingresos No Afectos a Impuestos (INAI)
                                   BigDecimal nonTaxableExpenses,    // Egresos No Afectos a Impuestos (ENAI)
                                   BigDecimal nonCashAdjustments,    // Ajustes de Gastos No Deseembolsables (AGDN)
                                   BigDecimal cashTimingAdjustment,  // Ajuste por diferencias entre reconocimiento y cobro o pago, incluidos impuestos.
                                   BigDecimal cashFlow,              // Flujo de Caja de ese periodo
                                   BigDecimal discountedCashFlow) {} // Resultado del VAN para ese periodo
