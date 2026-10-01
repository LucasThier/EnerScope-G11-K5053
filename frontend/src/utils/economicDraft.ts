import type { Driver, EconomicConfiguration, EconomicRule, Scenario } from '../types/economics';

export interface RuleDraft {
  id: string; concept: string; direction: 'INCOME' | 'EXPENSE'; driver: Driver;
  nodeId: string; value: string; unit: string; factor: string; from: string; to: string;
}
export interface AssetDraft {
  id: string; concept: string; nodeId: string; cost: string; residual: string;
  purchase: string; service: string; life: string;
}
export interface EconomicDraft {
  startYear: string; years: string; currency: string; wacc: string;
  entityId: string; entityName: string; taxRate: string;
  rules: RuleDraft[]; assets: AssetDraft[];
}
export const sections = ['General', 'Inversion', 'Ingresos', 'Costos', 'Impuestos'] as const;
export type Section = typeof sections[number];
export function emptyDraft(): EconomicDraft {
  return { startYear: String(new Date().getFullYear()), years: '10', currency: 'USD', wacc: '',
    entityId: crypto.randomUUID(), entityName: '', taxRate: '', rules: [], assets: [] };
}
const number = (value: string) => Number(value.replace(',', '.'));
const validNumber = (s: string, min = 0, max = Infinity) =>
  /^\d+(?:[.,]\d{1,12})?$/.test(s) && Number.isFinite(number(s)) && number(s) >= min && number(s) <= max;
const integer = (s: string, min: number, max: number) => /^\d+$/.test(s) && number(s) >= min && number(s) <= max;
const label = (s: string) => s.trim().length > 0 && s.length <= 120;
const endDate = (year: number) => `${year}-12-31`;
const startDate = (year: number) => `${year}-01-01`;
const realDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s;

export function validateDraft(d: EconomicDraft, nodes: Scenario['nodes']): Record<Section, string[]> {
  const errors: Record<Section, string[]> = { General: [], Inversion: [], Ingresos: [], Costos: [], Impuestos: [] };
  const start = number(d.startYear), end = start + number(d.years);
  if (!integer(d.startYear, 1900, 9000)) errors.General.push('Ingresá un año de inversión entre 1900 y 9000.');
  if (!integer(d.years, 1, 100)) errors.General.push('El horizonte debe tener entre 1 y 100 años.');
  if (!/^[A-Z]{3}$/.test(d.currency) || !Intl.supportedValuesOf('currency').includes(d.currency)) errors.General.push('Elegí una moneda ISO válida.');
  if (!validNumber(d.wacc)) errors.General.push('Ingresá un WACC no negativo.');
  if (!label(d.entityName)) errors.Impuestos.push('Ingresá el nombre de la entidad (hasta 120 caracteres).');
  if (!validNumber(d.taxRate, 0, 100)) errors.Impuestos.push('Ingresá una tasa entre 0 y 100 %.');
  const nodeExists = (id: string) => nodes.some(n => n.id === id);
  const concepts = new Set<string>();
  const register = (concept: string, section: Section) => {
    if (!label(concept)) errors[section].push('Cada concepto debe tener entre 1 y 120 caracteres.');
    if (concepts.has(concept)) errors[section].push('Los conceptos deben ser únicos, incluida la depreciación.');
    concepts.add(concept);
  };
  for (const a of d.assets) {
    register(a.concept, 'Inversion'); register(a.concept + '.depreciation', 'Inversion');
    if (!validNumber(a.cost) || !validNumber(a.residual) || number(a.residual) > number(a.cost)) errors.Inversion.push('Revisá el importe y el residual de ' + a.concept + '.');
    if (!integer(a.life, 1, 1200)) errors.Inversion.push('La vida útil debe tener entre 1 y 1200 meses.');
    if (!realDate(a.purchase) || +a.purchase.slice(0, 4) < start || +a.purchase.slice(0, 4) > end ||
        !realDate(a.service) || a.service < a.purchase) errors.Inversion.push('Revisá las fechas de compra y puesta en servicio.');
    if (a.nodeId && !nodeExists(a.nodeId)) errors.Inversion.push('Un activo referencia un nodo que ya no existe.');
  }
  const conversions = new Map<string, string>();
  for (const r of d.rules) {
    const section = r.direction === 'INCOME' ? 'Ingresos' : 'Costos';
    register(r.concept, section);
    if (!validNumber(r.value)) errors[section].push('Ingresá un importe o tarifa válido para ' + r.concept + '.');
    if (!integer(r.from, start + 1, end) || !integer(r.to, number(r.from), end)) errors[section].push('Los años de aplicación deben estar dentro del horizonte operativo.');
    if (r.driver !== 'FIXED') {
      if (!nodeExists(r.nodeId)) errors[section].push('Seleccioná un nodo válido.');
      if (!label(r.unit) || !validNumber(r.factor) || number(r.factor) <= 0) errors[section].push('Completá la unidad y un factor de conversión mayor que cero.');
      const key = r.nodeId + ':' + r.driver, value = r.unit + ':' + number(r.factor);
      if (conversions.has(key) && conversions.get(key) !== value) errors[section].push('Las reglas del mismo nodo y volumen deben compartir unidad y conversión.');
      conversions.set(key, value);
    }
  }
  return errors;
}

export function toConfiguration(d: EconomicDraft): EconomicConfiguration {
  const start = number(d.startYear), years = number(d.years), end = start + years;
  const entity = d.entityId;
  const c: EconomicConfiguration = {
    startYear: start, years, currency: d.currency, wacc: Number((number(d.wacc) / 100).toPrecision(15)),
    taxEntities: [{ id: entity, name: d.entityName, jurisdiction: 'Hypothetical', taxRate: Number((number(d.taxRate) / 100).toPrecision(15)),
      carryLosses: false, openingLoss: 0, lossExpiryYears: null, lossOffsetLimit: 1, taxPaymentLagYears: 0 }],
    boundary: [entity], nodeProfiles: [], adjustments: [], contracts: [], assets: [], taxTreatments: [], conversions: [],
  };
  const profile = (nodeId: string) => {
    let p = c.nodeProfiles.find(p => p.nodeId === nodeId);
    if (!p) { p = { nodeId, ownership: [{ taxEntityId: entity, share: 1 }], rules: [] }; c.nodeProfiles.push(p); }
    return p;
  };
  const treatment = (concept: string, classification: string) => c.taxTreatments.push({
    concept, taxEntityId: entity, validFrom: startDate(start), validTo: endDate(end),
    classification, deductibleFraction: classification === 'DEDUCTIBLE' ? 1 : 0,
  });
  for (const r of d.rules) {
    const rule: EconomicRule = { id: r.id, concept: r.concept, taxEntityId: entity, counterpartyId: null,
      direction: r.direction, cashClassification: 'CASH', driver: r.driver, unitValue: number(r.value),
      unit: r.driver === 'FIXED' ? d.currency : r.unit, currency: d.currency,
      validFrom: startDate(number(r.from)), validTo: endDate(number(r.to)),
      occurrences: Array.from({ length: number(r.to) - number(r.from) + 1 }, (_, i) => ({
        recognitionDate: endDate(number(r.from) + i), cashDate: endDate(number(r.from) + i),
      })),
    };
    if (r.driver === 'FIXED') c.adjustments.push(rule);
    else {
      profile(r.nodeId).rules.push(rule);
      if (!c.conversions.some(m => m.nodeId === r.nodeId && m.metric === r.driver)) c.conversions.push({
        nodeId: r.nodeId, metric: r.driver, rawUnit: 'SIMULATOR_UNIT', unit: r.unit, factor: number(r.factor), annualQuantity: null,
      });
    }
    treatment(r.concept, r.direction === 'INCOME' ? 'TAXABLE' : 'DEDUCTIBLE');
  }
  for (const a of d.assets) {
    if (a.nodeId) profile(a.nodeId);
    c.assets.push({ id: a.id, concept: a.concept, nodeId: a.nodeId || null, taxEntityId: entity,
      currency: d.currency, cost: number(a.cost), residualValue: number(a.residual),
      purchaseDate: a.purchase, paymentDate: a.purchase, serviceDate: a.service, usefulLifeMonths: number(a.life) });
    treatment(a.concept, 'CAPITALIZABLE'); treatment(a.concept + '.depreciation', 'DEDUCTIBLE');
  }
  return c;
}

// Compare all fields, including unknown future fields. Arrays here represent sets;
// occurrences remain protected because every date and duplicate participates.
function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).sort().join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k,v]) => JSON.stringify(k) + ':' + canonical(v)).join(',') + '}';
  return JSON.stringify(value);
}
export function fromConfiguration(c: EconomicConfiguration): EconomicDraft | null {
  try {
    if (c.taxEntities.length !== 1) return null;
    const entity = c.taxEntities[0];
    const d: EconomicDraft = { startYear: String(c.startYear), years: String(c.years), currency: c.currency,
      wacc: String(Number((c.wacc * 100).toPrecision(15))), entityId: entity.id, entityName: entity.name, taxRate: String(Number((entity.taxRate * 100).toPrecision(15))),
      assets: c.assets.map(a => ({ id: a.id, concept: a.concept, nodeId: a.nodeId ?? '', cost: String(a.cost),
        residual: String(a.residualValue), purchase: a.purchaseDate, service: a.serviceDate, life: String(a.usefulLifeMonths) })), rules: [] };
    const add = (r: EconomicRule, nodeId: string) => {
      if (!['FIXED', 'OUTPUT_VOLUME', 'EXPORTED_VOLUME'].includes(r.driver) ||
          (r.direction === 'EXPENSE' && r.driver === 'EXPORTED_VOLUME')) throw new Error('Unsupported driver');
      const conversion = c.conversions.find(m => m.nodeId === nodeId && m.metric === r.driver);
      d.rules.push({ id: r.id, concept: r.concept, direction: r.direction, driver: r.driver as Driver,
        nodeId, value: String(r.unitValue), unit: conversion?.unit ?? '', factor: String(conversion?.factor ?? ''),
        from: r.validFrom.slice(0, 4), to: r.validTo.slice(0, 4) });
    };
    c.adjustments.forEach(r => add(r, ''));
    c.nodeProfiles.forEach(p => p.rules.forEach(r => add(r, p.nodeId)));
    // Never silently erase contracts, tax policies, timing, ownership or unsupported fields.
    return canonical(toConfiguration(d)) === canonical(c) ? d : null;
  } catch { return null; }
}
