package org.enerscope.comparison.model.enums;

/** Reasons why the compared results are not strictly like for like. They never block a comparison. */
public enum ComparabilityWarning {
    DIFFERENT_CURRENCY("Los escenarios usan monedas distintas: los montos no se comparan entre sí."),
    DIFFERENT_START_YEAR("Los escenarios empiezan en años distintos."),
    DIFFERENT_HORIZON("Los escenarios tienen horizontes de evaluación distintos: los totales no son equivalentes."),
    DIFFERENT_WACC("Los escenarios usan tasas de descuento (WACC) distintas: VAN y payback descontado no son equivalentes."),
    MIXED_EVALUATION_MODES("Se comparan evaluaciones deterministas con probabilísticas: en estas últimas se usan las medias."),
    DIFFERENT_SIMULATION_VERSION("Los resultados físicos se calcularon con versiones distintas del simulador."),
    INCOMPLETE_RESULTS("Alguna evaluación no guarda todos los datos necesarios: los indicadores faltantes se muestran como no disponibles.");

    private final String message;

    ComparabilityWarning(String message) {
        this.message = message;
    }

    public String message() { return message; }
}
