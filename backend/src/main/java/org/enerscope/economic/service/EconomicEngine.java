package org.enerscope.economic.service;

import java.math.*;
import java.time.*;
import java.util.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.EconomicConfiguration.*;
import org.enerscope.economic.model.EconomicResult.*;
import org.springframework.stereotype.Service;
import static org.enerscope.economic.service.EconomicValidator.require;

/** Pure deterministic calculation. No database, clock, network or simulator state. */
@Service
public class EconomicEngine {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final MathContext MC = MathContext.DECIMAL128;
    private final EconomicValidator validator;
    public EconomicEngine(EconomicValidator validator) { this.validator = validator; }

    public EconomicResult calculate(EconomicConfiguration c, List<OperationalMetric> metrics) {
        require(c != null && c.nodeProfiles() != null, "Configuration and profiles required");
        validator.validate(c, c.nodeProfiles().stream().filter(Objects::nonNull).map(NodeEconomicProfile::nodeId).collect(java.util.stream.Collectors.toSet()));
        require(metrics != null, "Operational metrics required");
        Map<String, OperationalMetric> byMetric = new HashMap<>();
        for (OperationalMetric m : metrics) {
            require(m != null && m.quantity() != null && m.quantity().signum() >= 0 && m.unit() != null, "Invalid operational metric");
            require(byMetric.put(key(m.nodeId(), m.year(), m.metric()), m) == null, "Duplicate operational metric");
        }
        List<EconomicEntry> entries = new ArrayList<>();
        for (NodeEconomicProfile p : c.nodeProfiles())
            for (EconomicRule r : p.rules()) generate(c, r, p.nodeId(), null, byMetric, entries);
        for (EconomicRule r : c.adjustments()) generate(c, r, null, null, byMetric, entries);
        for (CommercialContract contract : c.contracts())
            generate(c, contract.revenueRule(), contract.deliveryNodeId(), contract.id(), byMetric, entries);
        for (CapitalAsset a : c.assets()) generateAsset(c, a, entries);
        entries.sort(Comparator.comparing(EconomicEntry::recognitionDate).thenComparing(EconomicEntry::id));

        List<EntityTaxResult> taxes = calculateTaxes(c, entries);
        List<PeriodEconomicResult> periods = new ArrayList<>();
        List<PendingBalance> pending = new ArrayList<>();
        BigDecimal npv = ZERO;
        for (int t = 0; t <= c.years(); t++) {
            int year = c.startYear() + t;
            BigDecimal iai = ZERO, eai = ZERO, gnd = ZERO, inai = ZERO, enai = ZERO, agnd = ZERO, actualCash = ZERO;
            for (EconomicEntry e : entries) {
                if (!included(c, e)) continue;
                BigDecimal a = e.amount(); boolean recognized = e.recognitionDate().getYear() == year;
                boolean paid = e.cashDate() != null && e.cashDate().getYear() == year;
                if (paid) actualCash = actualCash.add(e.direction() == Direction.INCOME ? a : a.negate());
                if (recognized && e.taxClassification() == TaxClassification.TAXABLE) {
                    iai = iai.add(a);
                    if (e.cashClassification() == CashClassification.NON_CASH) agnd = agnd.subtract(a);
                }
                if (e.taxClassification() == TaxClassification.DEDUCTIBLE) {
                    BigDecimal deductible = a.multiply(e.deductibleFraction(), MC);
                    if (recognized) {
                        if (e.cashClassification() == CashClassification.NON_CASH) { gnd = gnd.add(deductible); agnd = agnd.add(deductible); }
                        else eai = eai.add(deductible);
                    }
                    if (paid) enai = enai.add(a.subtract(deductible));
                } else if (paid && e.taxClassification() == TaxClassification.NON_TAXABLE) inai = inai.add(a);
                else if (paid && (e.taxClassification() == TaxClassification.NON_DEDUCTIBLE
                        || e.taxClassification() == TaxClassification.CAPITALIZABLE)) enai = enai.add(a);
            }
            BigDecimal tax = ZERO, taxPaid = ZERO;
            for (EntityTaxResult tr : taxes) if (c.boundary().contains(tr.taxEntityId())) {
                if (tr.year() == year) tax = tax.add(tr.tax());
                if (tr.paymentYear() == year) taxPaid = taxPaid.add(tr.tax());
            }
            // Round displayed sections first so the published table always reconciles exactly.
            iai = money(iai); eai = money(eai); gnd = money(gnd); inai = money(inai); enai = money(enai); agnd = money(agnd);
            BigDecimal rai = iai.subtract(eai).subtract(gnd), rdi = rai.subtract(tax);
            BigDecimal base = rdi.add(inai).subtract(enai).add(agnd);
            BigDecimal cash = money(actualCash.subtract(taxPaid));
            BigDecimal timing = cash.subtract(base);
            BigDecimal discounted = cash.divide(BigDecimal.ONE.add(c.wacc()).pow(t, MC), MC);
            npv = npv.add(discounted);
            periods.add(new PeriodEconomicResult(t, year, iai, eai, gnd, rai, money(tax), rdi,
                    inai, enai, agnd, timing, cash, money(discounted)));
        }
        int end = c.startYear() + c.years();
        for (EconomicEntry e : entries) if (included(c, e) && e.cashDate() != null && e.cashDate().getYear() > end)
            pending.add(new PendingBalance(e.id(), e.taxEntityId(), e.direction() == Direction.INCOME ? "RECEIVABLE" : "PAYABLE", e.amount(), e.cashDate()));
        for (EntityTaxResult t : taxes) if (c.boundary().contains(t.taxEntityId()) && t.paymentYear() > end && t.tax().signum() > 0)
            pending.add(new PendingBalance("tax:" + t.year(), t.taxEntityId(), "TAX_PAYABLE", t.tax(), LocalDate.of(t.paymentYear(), 12, 31)));
        return new EconomicResult(List.copyOf(entries), List.copyOf(metrics), List.copyOf(periods), List.copyOf(taxes), List.copyOf(pending), money(npv));
    }

    private void generate(EconomicConfiguration c, EconomicRule r, UUID nodeId, String contract,
            Map<String, OperationalMetric> metrics, List<EconomicEntry> out) {
        for (Occurrence o : r.occurrences()) {
            BigDecimal quantity = BigDecimal.ONE;
            if (r.driver() != Driver.FIXED) {
                OperationalMetric m = metrics.get(key(nodeId, o.recognitionDate().getYear(), r.driver()));
                require(m != null && r.unit().equals(m.unit()), "Missing metric or incompatible unit for " + r.id());
                quantity = m.quantity();
            }
            allocate(c, nodeId, r.taxEntityId(), r.counterpartyId(), r.id(), contract, r.concept(), r.direction(),
                    r.cashClassification(), o.recognitionDate(), o.cashDate(), money(quantity.multiply(r.unitValue(), MC)), out, null);
        }
    }

    private void generateAsset(EconomicConfiguration c, CapitalAsset a, List<EconomicEntry> out) {
        allocate(c, a.nodeId(), a.taxEntityId(), null, "asset:" + a.id(), null, a.concept(), Direction.EXPENSE,
                CashClassification.CASH, a.purchaseDate(), a.paymentDate(), money(a.cost()), out, TaxClassification.CAPITALIZABLE);
        BigDecimal basis = a.cost().subtract(a.residualValue());
        BigDecimal monthly = basis.divide(BigDecimal.valueOf(a.usefulLifeMonths()), MC);
        Map<Integer, BigDecimal> annual = new TreeMap<>();
        BigDecimal assigned = ZERO;
        YearMonth service = YearMonth.from(a.serviceDate());
        for (int month = 0; month < a.usefulLifeMonths(); month++) {
            BigDecimal depreciation = month == a.usefulLifeMonths() - 1 ? basis.subtract(assigned) : monthly;
            assigned = assigned.add(depreciation);
            int year = service.plusMonths(month).getYear();
            if (year >= c.startYear() && year <= c.startYear() + c.years()) annual.merge(year, depreciation, BigDecimal::add);
        }
        // Cumulative rounding assigns the final cent only once across annual depreciation amounts.
        BigDecimal cumulative = ZERO, rounded = ZERO;
        for (var part : annual.entrySet()) {
            cumulative = cumulative.add(part.getValue()); BigDecimal amount = money(cumulative).subtract(rounded); rounded = money(cumulative);
            allocate(c, a.nodeId(), a.taxEntityId(), null, "depreciation:" + a.id(), null, a.concept() + ".depreciation",
                    Direction.EXPENSE, CashClassification.NON_CASH, LocalDate.of(part.getKey(), 12, 31), null, amount, out, TaxClassification.DEDUCTIBLE);
        }
    }

    private void allocate(EconomicConfiguration c, UUID node, String entity, String counterparty, String source,
            String contract, String concept, Direction direction, CashClassification cash, LocalDate recognition,
            LocalDate payment, BigDecimal total, List<EconomicEntry> out, TaxClassification expected) {
        List<Ownership> owners = entity != null ? List.of(new Ownership(entity, BigDecimal.ONE))
                : c.nodeProfiles().stream().filter(p -> p.nodeId().equals(node)).findFirst().orElseThrow().ownership();
        BigDecimal assigned = ZERO;
        for (int i = 0; i < owners.size(); i++) {
            Ownership o = owners.get(i);
            BigDecimal amount = i == owners.size() - 1 ? total.subtract(assigned) : money(total.multiply(o.share(), MC));
            assigned = assigned.add(amount);
            add(c, node, o.taxEntityId(), counterparty, source, contract, concept, direction, cash, recognition, payment, amount, out, expected);
            if (counterparty != null) add(c, node, counterparty, o.taxEntityId(), source + ":counterparty", contract, concept,
                    direction == Direction.INCOME ? Direction.EXPENSE : Direction.INCOME, cash, recognition, payment, amount, out, null);
        }
    }

    private void add(EconomicConfiguration c, UUID node, String entity, String counterparty, String source, String contract,
            String concept, Direction direction, CashClassification cash, LocalDate recognition, LocalDate payment,
            BigDecimal amount, List<EconomicEntry> out, TaxClassification expected) {
        TaxTreatment treatment = c.taxTreatments().stream().filter(t -> t.concept().equals(concept) && t.taxEntityId().equals(entity)
                && !recognition.isBefore(t.validFrom()) && !recognition.isAfter(t.validTo())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing tax treatment: " + concept + "/" + entity + "/" + recognition));
        require(expected == null || treatment.classification() == expected, "Incorrect asset tax treatment: " + concept);
        require(treatment.classification() != TaxClassification.CAPITALIZABLE || expected == TaxClassification.CAPITALIZABLE,
                "Capitalizable purchases must be modeled as assets, not duplicated as rules");
        boolean incomeClass = treatment.classification() == TaxClassification.TAXABLE || treatment.classification() == TaxClassification.NON_TAXABLE;
        require(incomeClass == (direction == Direction.INCOME), "Tax treatment conflicts with direction: " + concept);
        require(treatment.classification() != TaxClassification.CAPITALIZABLE || cash == CashClassification.CASH,
                "Capitalized purchases must be cash entries");
        out.add(new EconomicEntry(source + ":" + entity + ":" + recognition, node, entity, counterparty, concept, source,
                contract, direction, treatment.classification(), cash, recognition, payment, amount, c.currency(), treatment.deductibleFraction()));
    }

    private List<EntityTaxResult> calculateTaxes(EconomicConfiguration c, List<EconomicEntry> entries) {
        List<EntityTaxResult> result = new ArrayList<>();
        for (TaxEntity entity : c.taxEntities()) {
            LinkedList<Loss> losses = new LinkedList<>();
            if (entity.openingLoss().signum() > 0) losses.add(new Loss(c.startYear() - 1, entity.openingLoss()));
            for (int year = c.startYear(); year <= c.startYear() + c.years(); year++) {
                BigDecimal expired = ZERO;
                while (!losses.isEmpty() && entity.lossExpiryYears() != null && year - losses.getFirst().year > entity.lossExpiryYears())
                    expired = expired.add(losses.removeFirst().amount);
                BigDecimal rai = ZERO;
                for (EconomicEntry e : entries) if (e.taxEntityId().equals(entity.id()) && e.recognitionDate().getYear() == year) {
                    if (e.taxClassification() == TaxClassification.TAXABLE) rai = rai.add(e.amount());
                    if (e.taxClassification() == TaxClassification.DEDUCTIBLE) rai = rai.subtract(e.amount().multiply(e.deductibleFraction(), MC));
                }
                rai = money(rai); BigDecimal used = ZERO;
                BigDecimal available = rai.max(ZERO).multiply(entity.lossOffsetLimit(), MC);
                while (available.signum() > 0 && !losses.isEmpty()) {
                    Loss l = losses.getFirst(); BigDecimal amount = l.amount.min(available);
                    used = used.add(amount); available = available.subtract(amount); l.amount = l.amount.subtract(amount);
                    if (l.amount.signum() == 0) losses.removeFirst();
                }
                if (rai.signum() < 0 && entity.carryLosses()) losses.add(new Loss(year, rai.negate()));
                BigDecimal base = rai.subtract(used).max(ZERO);
                BigDecimal closing = losses.stream().map(l -> l.amount).reduce(ZERO, BigDecimal::add);
                result.add(new EntityTaxResult(entity.id(), year, rai, money(used), money(expired), money(closing),
                        money(base), money(base.multiply(entity.taxRate(), MC)), year + entity.taxPaymentLagYears()));
            }
        }
        return result;
    }
    private static final class Loss { final int year; BigDecimal amount; Loss(int year, BigDecimal amount) { this.year = year; this.amount = amount; } }
    private boolean included(EconomicConfiguration c, EconomicEntry e) {
        return c.boundary().contains(e.taxEntityId()) && (e.counterpartyId() == null || !c.boundary().contains(e.counterpartyId()));
    }
    private static String key(UUID node, int year, Driver driver) { return node + ":" + year + ":" + driver; }
    public static BigDecimal money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }
}
