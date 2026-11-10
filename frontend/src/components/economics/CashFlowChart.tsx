import { useEffect, useRef, useState } from 'react';
import type { EconomicPeriod } from '../../types/economics';
import { cashFlowScale } from '../../utils/economicChart';
import { money } from '../../utils/economicFormat';

type Detail = { period: number; x: number; y: number };

export function CashFlowChart({ periods, currency, active }: { periods: EconomicPeriod[]; currency?: string; active: boolean }) {
  const chart = useRef<SVGSVGElement>(null);
  const [hover, setHover] = useState<Detail | null>(null);
  const [focus, setFocus] = useState<Detail | null>(null);
  const detail = hover ?? focus;
  const selected = periods.find(p => p.period === detail?.period);
  const scale = cashFlowScale(periods.map(p => p.cashFlow));

  useEffect(() => {
    if (!active) return;
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const animations: Animation[] = [];
    if (!preference.matches) chart.current?.querySelectorAll('rect').forEach((bar, index) => {
      animations.push(bar.animate([{ transform: 'scaleY(0)' }, { transform: 'scaleY(1)' }], {
        duration: 550, delay: Math.min(index * 45, 350), easing: 'cubic-bezier(0.22, 1, 0.36, 1)', fill: 'backwards',
      }));
    });
    const stop = () => animations.forEach(animation => animation.cancel());
    preference.addEventListener('change', stop);
    return () => { stop(); preference.removeEventListener('change', stop); };
  }, [active, periods]);

  useEffect(() => {
    const dismiss = () => { setHover(null); setFocus(null); };
    window.addEventListener('scroll', dismiss, true);
    window.addEventListener('resize', dismiss);
    return () => { window.removeEventListener('scroll', dismiss, true); window.removeEventListener('resize', dismiss); };
  }, []);

  return <div className="overflow-x-auto">
    {active && detail && selected && <div role="tooltip" className="pointer-events-none fixed z-50 w-72 max-w-[calc(100vw-16px)] rounded-lg bg-ink-800 px-3 py-2 text-sm text-white shadow-lg"
      style={{ left: Math.max(8, Math.min(detail.x + 14, window.innerWidth - 296)), top: Math.max(8, Math.min(detail.y + 14, window.innerHeight - 72)) }}>
      Año {selected.year} · {money(selected.cashFlow, currency)}
    </div>}
    <svg ref={chart} className="min-w-full" width={Math.max(640, periods.length * 65 + 90)} height="300" role="group" aria-label="Flujos anuales: barras hacia arriba positivas y hacia abajo negativas. Valores exactos en la tabla siguiente.">
      <g>{scale.ticks.map(tick => <g key={tick}><line x1="9%" x2="99%" y1={scale.y(tick)} y2={scale.y(tick)} className={tick === 0 ? 'stroke-ink-300' : 'stroke-ink-100'} strokeDasharray={tick === 0 ? undefined : '4 4'} /><text x="8%" y={scale.y(tick) + 4} textAnchor="end" className="fill-ink-500 text-xs">{new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 }).format(tick)}</text></g>)}</g>
      {periods.map((p, i) => {
        const h = Math.abs(scale.y(p.cashFlow) - scale.zero);
        const center = 9 + (i + 0.5) / periods.length * 90;
        const barWidth = Math.min(4, 50 / periods.length);
        const x = (center - barWidth / 2) + '%';
        return <g key={p.period}>
          <rect tabIndex={0} role="img" aria-label={'Año ' + p.year + ': ' + money(p.cashFlow, currency)}
            onPointerMove={event => setHover({ period: p.period, x: event.clientX, y: event.clientY })}
            onPointerLeave={() => setHover(null)}
            onFocus={event => { const bounds = event.currentTarget.getBoundingClientRect(); setFocus({ period: p.period, x: bounds.right, y: bounds.top }); }}
            onBlur={() => setFocus(null)}
            onClick={event => setHover({ period: p.period, x: event.clientX, y: event.clientY })}
            onKeyDown={event => { if (event.key === 'Escape') { setHover(null); setFocus(null); } }}
            x={x} y={Math.min(scale.y(p.cashFlow), scale.zero)} width={barWidth + '%'} height={Math.max(h, 1)}
            style={{ transformOrigin: '0 ' + scale.zero + 'px' }} className={p.cashFlow >= 0 ? 'fill-brand-800' : 'fill-ink-400'} />
          <text x={center + '%'} y="284" textAnchor="middle" className="fill-ink-600 text-xs">{p.year}</text>
        </g>;
      })}
    </svg>
  </div>;
}
