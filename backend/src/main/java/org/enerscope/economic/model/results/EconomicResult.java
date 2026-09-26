package org.enerscope.economic.model.results;

import org.enerscope.economic.model.*;

import java.math.BigDecimal;
import java.util.List;

public record EconomicResult(List<EconomicEntry> entries,                 // Movimientos utilizados en la evaluación
                             List<OperationalMetric> operationalMetrics,  // Métricas operativas utilizadas
                             List<PeriodEconomicResult> periods,          // Resultados consolidados de cada período
                             List<EntityTaxResult> entityTaxes,// Cálculos fiscales (Impuestos) por entidad y año
                             List<PendingBalance> pendingBalances,// Importes pendientes de cobro o pago al final del horizonte
                             BigDecimal npv) {}                           // Resultado del VAN
