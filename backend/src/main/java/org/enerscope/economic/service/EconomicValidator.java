package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.enerscope.economic.model.EconomicConfiguration;
import org.enerscope.economic.model.EconomicConfiguration.*;
import org.springframework.stereotype.Component;

@Component
public class EconomicValidator {
    public void validate(EconomicConfiguration c, Set<UUID> nodeIds) {
        require(c != null, "Configuration is required");
        require(c.startYear() >= 1900 && c.startYear() <= 9000 && c.years() > 0 && c.years() <= 100,
                "Use a valid start year and a horizon from 1 to 100 years");
        require(c.wacc() != null && c.wacc().signum() >= 0, "WACC must be nonnegative");
        require(c.currency() != null && c.currency().matches("[A-Z]{3}"), "Use a three-letter currency");
        try { Currency.getInstance(c.currency()); } catch (Exception e) { throw new IllegalArgumentException("Unknown currency"); }
        require(c.taxEntities() != null && !c.taxEntities().isEmpty() && c.boundary() != null && !c.boundary().isEmpty(),
                "Tax entities and boundary are required");
        require(c.nodeProfiles() != null && c.adjustments() != null && c.contracts() != null && c.assets() != null
                && c.taxTreatments() != null && c.conversions() != null, "All configuration collections are required");
        Set<String> entities = new HashSet<>();
        for (TaxEntity e : c.taxEntities()) {
            require(e != null, "Null tax entity");
            identifier(e.id());
            require(entities.add(e.id()), "Duplicate tax entity");
            require(e.name() != null && !e.name().isBlank() && e.jurisdiction() != null, "Entity name/jurisdiction required");
            fraction(e.taxRate()); fraction(e.lossOffsetLimit()); nonnegative(e.openingLoss());
            require(e.lossExpiryYears() == null || e.lossExpiryYears() > 0, "Loss expiry must be positive");
            require(e.taxPaymentLagYears() >= 0 && e.taxPaymentLagYears() <= 100, "Invalid tax payment lag");
            require(e.carryLosses() || e.openingLoss().signum() == 0, "Opening losses require carry-forward");
        }
        require(entities.containsAll(c.boundary()), "Unknown entity in boundary");
        for (TaxTreatment t : c.taxTreatments()) {
            require(t != null && t.classification() != null && entities.contains(t.taxEntityId()), "Invalid tax treatment");
            identifier(t.concept()); dates(t.validFrom(), t.validTo()); fraction(t.deductibleFraction());
            require(t.classification() == TaxClassification.DEDUCTIBLE || t.deductibleFraction().signum() == 0,
                    "Only deductible treatments accept a deductible fraction");
            String key = t.concept() + ":" + t.taxEntityId();
            for (TaxTreatment other : c.taxTreatments()) {
                if (other == t) break;
                if (other.concept().equals(t.concept()) && other.taxEntityId().equals(t.taxEntityId()))
                    require(t.validTo().isBefore(other.validFrom()) || t.validFrom().isAfter(other.validTo()),
                            "Overlapping tax treatments: " + key);
            }
        }
        Set<UUID> profiles = new HashSet<>(); Set<String> rules = new HashSet<>();
        for (NodeEconomicProfile p : c.nodeProfiles()) {
            require(p != null && nodeIds.contains(p.nodeId()) && profiles.add(p.nodeId()), "Invalid or duplicate node profile");
            require(p.ownership() != null && !p.ownership().isEmpty() && p.rules() != null, "Ownership and rules required");
            BigDecimal total = BigDecimal.ZERO; Set<String> owners = new HashSet<>();
            for (Ownership o : p.ownership()) {
                require(o != null && entities.contains(o.taxEntityId()) && owners.add(o.taxEntityId()), "Invalid owner");
                fraction(o.share()); require(o.share().signum() > 0, "Ownership must be positive"); total = total.add(o.share());
            }
            require(total.compareTo(BigDecimal.ONE) == 0, "Node ownership must sum to 1");
            for (EconomicRule r : p.rules()) rule(c, r, p.nodeId(), entities, rules);
        }
        for (EconomicRule r : c.adjustments()) rule(c, r, null, entities, rules);
        Set<String> contracts = new HashSet<>();
        for (CommercialContract contract : c.contracts()) {
            require(contract != null && profiles.contains(contract.deliveryNodeId()), "Contract delivery node needs a profile");
            identifier(contract.id()); require(contracts.add(contract.id()), "Duplicate contract");
            require(contract.revenueRule() != null && contract.revenueRule().direction() == Direction.INCOME,
                    "Contract must generate income");
            require(Set.of(Driver.FIXED, Driver.EXPORTED_VOLUME, Driver.OUTPUT_VOLUME).contains(contract.revenueRule().driver()),
                    "Contract requires fixed price or delivered quantity");
            rule(c, contract.revenueRule(), contract.deliveryNodeId(), entities, rules);
        }
        Set<String> assets = new HashSet<>();
        for (CapitalAsset a : c.assets()) {
            require(a != null, "Null asset"); identifier(a.id()); identifier(a.concept());
            require(assets.add(a.id()), "Duplicate asset");
            require(a.nodeId() == null || profiles.contains(a.nodeId()), "Asset node needs a profile");
            require(a.taxEntityId() != null ? entities.contains(a.taxEntityId()) : a.nodeId() != null, "Asset needs an owner");
            require(c.currency().equals(a.currency()), "Mixed currencies are not supported");
            nonnegative(a.cost()); nonnegative(a.residualValue());
            require(a.residualValue().compareTo(a.cost()) <= 0 && a.usefulLifeMonths() > 0 && a.usefulLifeMonths() <= 1200,
                    "Invalid depreciation basis or useful life");
            recognition(c, a.purchaseDate()); require(a.serviceDate() != null && !a.serviceDate().isBefore(a.purchaseDate()), "Invalid service date");
            payment(a.paymentDate());
            require(a.paymentDate().getYear() >= c.startYear(), "Asset payment precedes evaluation");
        }
        Set<String> conversions = new HashSet<>();
        for (MetricConversion m : c.conversions()) {
            require(m != null && profiles.contains(m.nodeId()) && m.metric() != null && m.metric() != Driver.FIXED,
                    "Invalid metric conversion");
            identifier(m.rawUnit()); identifier(m.unit()); nonnegative(m.factor());
            require(m.factor().signum() > 0, "Conversion factor must be positive");
            require(conversions.add(m.nodeId() + ":" + m.metric()), "Duplicate metric conversion");
            boolean observed = observed(m.metric());
            if (observed) {
                String expected = switch (m.metric()) {
                    case OPERATING_HOURS -> "HOUR"; case EVENT_COUNT -> "EVENT"; default -> "SIMULATOR_UNIT";
                };
                require(expected.equals(m.rawUnit()), "Raw metric unit must be " + expected);
            }
            require(observed ? m.annualQuantity() == null : m.annualQuantity() != null,
                    "Observed metrics cannot override simulation; static metrics require annualQuantity");
            if (m.annualQuantity() != null) nonnegative(m.annualQuantity());
        }
    }

    private void rule(EconomicConfiguration c, EconomicRule r, UUID node, Set<String> entities, Set<String> ids) {
        require(r != null, "Null rule"); identifier(r.id()); identifier(r.concept());
        require(!r.id().contains(":"), "Rule IDs cannot contain reserved colon separators");
        require(ids.add(r.id()), "Rule IDs must be unique throughout the configuration");
        require(r.taxEntityId() != null ? entities.contains(r.taxEntityId()) : node != null, "Rule needs an owner");
        require(r.counterpartyId() == null || (entities.contains(r.counterpartyId()) && r.taxEntityId() != null
                && !r.counterpartyId().equals(r.taxEntityId())), "Internal transfers need distinct explicit entities");
        require(r.direction() != null && r.cashClassification() != null && r.driver() != null, "Rule classifications required");
        nonnegative(r.unitValue()); identifier(r.unit()); dates(r.validFrom(), r.validTo());
        require(c.currency().equals(r.currency()), "Mixed currencies are not supported");
        require(r.driver() == Driver.FIXED || node != null, "Variable rules need a node");
        if (r.driver() != Driver.FIXED) {
            require(c.conversions().stream().anyMatch(m -> m != null && node.equals(m.nodeId())
                    && r.driver() == m.metric() && r.unit().equals(m.unit())), "Missing compatible metric conversion: " + r.id());
        }
        require(r.occurrences() != null && !r.occurrences().isEmpty(), "Rule occurrences required");
        Set<LocalDate> seen = new HashSet<>();
        for (Occurrence o : r.occurrences()) {
            require(o != null, "Null occurrence"); recognition(c, o.recognitionDate());
            require(seen.add(o.recognitionDate()), "Duplicate rule occurrence");
            require(!o.recognitionDate().isBefore(r.validFrom()) && !o.recognitionDate().isAfter(r.validTo()), "Occurrence outside rule validity");
            if (r.cashClassification() == CashClassification.CASH) {
                payment(o.cashDate()); require(o.cashDate().getYear() >= c.startYear(), "Cash precedes evaluation");
            } else require(o.cashDate() == null, "Non-cash entries cannot have cash dates");
        }
    }
    public static boolean observed(Driver d) {
        return Set.of(Driver.INPUT_VOLUME, Driver.OUTPUT_VOLUME, Driver.EXPORTED_VOLUME, Driver.OPERATING_HOURS, Driver.EVENT_COUNT).contains(d);
    }
    public static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private void identifier(String s) { require(s != null && !s.isBlank() && s.length() <= 120, "Nonblank identifier (max 120 characters) required"); }
    private void nonnegative(BigDecimal v) { require(v != null && v.signum() >= 0 && v.precision() <= 30 && v.scale() <= 12, "Invalid nonnegative decimal"); }
    private void fraction(BigDecimal v) { nonnegative(v); require(v.compareTo(BigDecimal.ONE) <= 0, "Fraction must be between 0 and 1"); }
    private void dates(LocalDate a, LocalDate b) { require(a != null && b != null && !b.isBefore(a), "Invalid validity range"); }
    private void payment(LocalDate d) { require(d != null && d.getYear() >= 1900 && d.getYear() <= 9999, "Cash date required"); }
    private void recognition(EconomicConfiguration c, LocalDate d) {
        require(d != null && d.getYear() >= c.startYear() && d.getYear() <= c.startYear() + c.years(), "Recognition outside horizon");
    }
}
