import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

/**
 * Dependency-free charts share the workspace palette. Every hover target also supports
 * keyboard focus and touch; values remain available in the analytics table view.
 */
const CHART_TOKENS = `
  :host {
    --viz-series-1: var(--hg-primary); --viz-series-2: var(--hg-lavender);
    --viz-text: var(--hg-text); --viz-text-2: var(--hg-muted); --viz-grid: var(--hg-border); --viz-surface: var(--hg-surface);
    --viz-tip-bg: var(--hg-text); --viz-tip-text: var(--hg-bg);
    display: block; position: relative;
  }
  .tip { position: absolute; z-index: 5; pointer-events: none; background: var(--viz-tip-bg); color: var(--viz-tip-text);
    border-radius: 8px; padding: 6px 9px; font-size: 12px; line-height: 1.4; white-space: nowrap;
    box-shadow: 0 4px 14px rgba(0,0,0,0.18); transform: translate(-50%, calc(-100% - 8px)); }
  .tip b { font-weight: 600; }
  .empty { color: var(--viz-text-2); font-size: 12px; padding: 24px 0; }
  [tabindex]:focus-visible { outline: 2px solid var(--hg-primary); outline-offset: 3px; border-radius: 4px; }
  .tip.edge-start { transform: translate(0, calc(-100% - 8px)); }
  .tip.edge-end { transform: translate(-100%, calc(-100% - 8px)); }
  .key { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: -1px; }
`;

export interface BarDatum { label: string; value: number; }

// ============================================================ horizontal bars (ranked categories)
@Component({
  selector: 'hg-hbar-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rows" [attr.role]="data().length && !clickable() ? 'list' : null">
      @for (d of data(); track d.label; let i = $index) {
        <div class="row" [class.clickable]="clickable()" [attr.role]="clickable() ? 'button' : 'listitem'" tabindex="0"
             (focus)="hover.set(i)" (blur)="hover.set(null)" (click)="pick(i, d)" (keydown.enter)="pick(i, d)" (keydown.space)="$event.preventDefault(); pick(i, d)"
             (mouseenter)="hover.set(i)" (mouseleave)="hover.set(null)"
             [attr.aria-label]="d.label + ': ' + fmt()(d.value) + (clickable() ? '. ' + actionLabel() : '')">
          <span class="label" [title]="d.label">{{ d.label }}</span>
          <span class="track"><i [style.width.%]="pct(d.value)" [class.dim]="hover() !== null && hover() !== i"></i></span>
          <span class="value">{{ fmt()(d.value) }}@if (clickable()) {<span class="go" aria-hidden="true">›</span>}</span>
        </div>
      } @empty {
        <p class="empty">No data yet.</p>
      }
    </div>
  `,
  styles: CHART_TOKENS + `
    .rows { display: grid; gap: 2px; }
    .row { display: grid; grid-template-columns: minmax(90px, 34%) 1fr auto; align-items: center; gap: 10px;
      padding: 10px 0; border-radius: 6px; cursor: default; }
    .row:hover { background: color-mix(in srgb, var(--viz-grid) 45%, transparent); }
    .label { font-size: 13px; color: var(--viz-text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .track { height: 10px; background: var(--hg-tint); border-radius: 5px; }
    .track i { display: block; height: 100%; background: var(--viz-series-1); border-radius: 5px;
      transition: opacity 160ms, width 240ms; }
    .track i.dim { opacity: 0.45; }
    .value { font-size: 13px; font-variant-numeric: tabular-nums; color: var(--viz-text); min-width: 3ch; text-align: right; }
    .empty { font-size: 13px; color: var(--viz-text-2); }
    .row.clickable { cursor: pointer; padding-right: 4px; }
    .row.clickable:hover .label, .row.clickable:focus-visible .label { color: var(--viz-text); text-decoration: underline; text-underline-offset: 3px; }
    .go { margin-left: 8px; color: var(--viz-text-2); font-size: 15px; }
  `
})
export class HBarChartComponent {
  data = input.required<BarDatum[]>();
  fmt = input<(v: number) => string>((v) => v.toLocaleString('en-IN'));
  /** When set, each bar is a button that emits {@link selected} (drill-down). */
  clickable = input(false);
  actionLabel = input('Show details');
  selected = output<BarDatum>();

  hover = signal<number | null>(null);

  pick(i: number, d: BarDatum): void {
    this.hover.set(i);
    if (this.clickable()) {
      this.selected.emit(d);
    }
  }
  private max = computed(() => Math.max(1, ...this.data().map((d) => d.value)));

  pct(v: number): number {
    return (100 * v) / this.max();
  }
}

// ============================================================ grouped columns (over time)
export interface ColumnSeries { name: string; values: number[]; }

@Component({
  selector: 'hg-column-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (series().length > 1) {
      <div class="legend">
        @for (s of series(); track s.name; let si = $index) {
          <span><span class="key" [style.background]="'var(--viz-series-' + (si + 1) + ')'"></span>{{ s.name }}</span>
        }
      </div>
    }
    <div class="plot" [style.height.px]="height()">
      @for (g of gridLines(); track g) {
        <div class="grid" [style.bottom.%]="100 * g / scaleMax()"><span>{{ fmt()(g) }}</span></div>
      }
      <div class="cols">
        @for (lbl of labels(); track lbl; let i = $index) {
          <div class="col" [class.clickable]="clickable()" tabindex="0" (focus)="hover.set(i)" (blur)="hover.set(null)"
               (click)="pick(i)" (keydown.enter)="pick(i)" (keydown.space)="$event.preventDefault(); pick(i)" (keydown.escape)="hover.set(null)"
               (mouseenter)="hover.set(i)" (mouseleave)="hover.set(null)"
               [attr.aria-label]="lbl + ': ' + describe(i) + (clickable() ? '. ' + actionLabel() : '')" [attr.role]="clickable() ? 'button' : 'img'">
            <div class="bars">
              @for (s of series(); track s.name; let si = $index) {
                <i [style.height.%]="100 * s.values[i] / scaleMax()"
                   [style.background]="'var(--viz-series-' + (si + 1) + ')'"
                   [class.dim]="hover() !== null && hover() !== i"></i>
              }
            </div>
            @if (hover() === i) {
              <div class="tip" [class.edge-start]="i === 0" [class.edge-end]="i === labels().length - 1" [style.left.%]="50" [style.top.px]="tipTop(i)">
                <b>{{ lbl }}</b>
                @for (s of series(); track s.name; let si = $index) {
                  <div><span class="key" [style.background]="'var(--viz-series-' + (si + 1) + ')'"></span>{{ s.name }}: {{ fmt()(s.values[i]) }}</div>
                }
              </div>
            }
          </div>
        }
      </div>
    </div>
    <div class="xlabels">
      @for (lbl of labels(); track lbl; let i = $index) {
        <span [class.hide]="labels().length > 8 && i % 2 === 1">{{ short()(lbl) }}</span>
      }
    </div>
  `,
  styles: CHART_TOKENS + `
    .legend { display: flex; gap: 16px; font-size: 12px; color: var(--viz-text-2); margin-bottom: 8px; }
    .plot { position: relative; margin-left: 44px; border-bottom: 1px solid var(--viz-grid); }
    .grid { position: absolute; left: 0; right: 0; border-top: 1px solid var(--viz-grid); }
    .grid span { position: absolute; right: calc(100% + 6px); top: -8px; font-size: 11px; color: var(--viz-text-2);
      font-variant-numeric: tabular-nums; white-space: nowrap; }
    .cols { position: absolute; inset: 0; display: flex; gap: 2px; }
    .col { flex: 1; position: relative; display: flex; align-items: flex-end; justify-content: center; cursor: default; }
    .col:hover { background: color-mix(in srgb, var(--viz-grid) 40%, transparent); }
    .bars { display: flex; gap: 2px; align-items: flex-end; height: 100%; width: 70%; max-width: 44px; }
    .bars i { flex: 1; min-height: 0; border-radius: 4px 4px 0 0; transition: opacity 120ms; }
    .bars i.dim { opacity: 0.45; }
    .xlabels { display: flex; gap: 2px; margin-left: 44px; margin-top: 6px; }
    .xlabels span { flex: 1; text-align: center; font-size: 11px; color: var(--viz-text-2); white-space: nowrap; overflow: hidden; }
    .xlabels span.hide { visibility: hidden; }
    .col.clickable { cursor: pointer; }
  `
})
export class ColumnChartComponent {
  labels = input.required<string[]>();
  series = input.required<ColumnSeries[]>();
  height = input(180);
  fmt = input<(v: number) => string>((v) => v.toLocaleString('en-IN'));
  short = input<(l: string) => string>((l) => l);
  /** When set, each column is a button that emits its index in {@link selected} (drill-down). */
  clickable = input(false);
  actionLabel = input('Show details');
  selected = output<number>();

  hover = signal<number | null>(null);

  pick(i: number): void {
    this.hover.set(i);
    if (this.clickable()) {
      this.selected.emit(i);
    }
  }

  private rawMax = computed(() => Math.max(1, ...this.series().flatMap((s) => s.values)));
  scaleMax = computed(() => niceCeil(this.rawMax()));
  gridLines = computed(() => [this.scaleMax() / 2, this.scaleMax()]);

  describe(i: number): string {
    return this.series().map((s) => `${s.name} ${this.fmt()(s.values[i])}`).join(', ');
  }

  tipTop(i: number): number {
    const top = Math.max(...this.series().map((s) => s.values[i] ?? 0));
    return this.height() * (1 - top / this.scaleMax());
  }
}

// ============================================================ line (one measure over time)
@Component({
  selector: 'hg-line-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (values().length && labels().length) {
    <div class="wrap" [style.height.px]="height()" (mouseleave)="hover.set(null)">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" aria-hidden="true">
        @for (g of gridLines(); track g) {
          <line [attr.x1]="0" [attr.x2]="W" [attr.y1]="y(g)" [attr.y2]="y(g)" class="grid" />
        }
        <polygon [attr.points]="area()" class="area" />
        <polyline [attr.points]="points()" class="line" vector-effect="non-scaling-stroke" />
        @if (hover() !== null) {
          <line [attr.x1]="x(hover()!)" [attr.x2]="x(hover()!)" y1="0" [attr.y2]="H" class="cross" vector-effect="non-scaling-stroke" />
        }
      </svg>
      @if (values().length === 1) { <span class="dot" [style.left.%]="50" [style.top.%]="100 * y(values()[0]) / H"></span> }
      @if (hover() !== null) {
        <span class="dot" [style.left.%]="100 * x(hover()!) / W" [style.top.%]="100 * y(values()[hover()!]) / H"></span>
        <div class="tip" [class.edge-start]="hover() === 0" [class.edge-end]="hover() === values().length - 1 && values().length > 1" [style.left.%]="100 * x(hover()!) / W" [style.top.%]="100 * y(values()[hover()!]) / H">
          <b>{{ labels()[hover()!] }}</b><div>{{ name() }}: {{ fmt()(values()[hover()!]) }}</div>
        </div>
      }
      <div class="hits">
        @for (l of labels(); track l; let i = $index) {
          <span tabindex="0" (focus)="hover.set(i)" (blur)="hover.set(null)" (click)="hover.set(i)" (keydown.escape)="hover.set(null)" (mouseenter)="hover.set(i)" [attr.aria-label]="l + ': ' + fmt()(values()[i])" role="img"></span>
        }
      </div>
      @for (g of gridLines(); track g) {
        <span class="ylab" [style.top.%]="100 * y(g) / H">{{ fmt()(g) }}</span>
      }
    </div>
    <div class="xlabels">
      <span>{{ short()(labels()[0]) }}</span><span>{{ short()(labels()[labels().length - 1]) }}</span>
    </div>
    } @else { <p class="empty">No history available yet.</p> }
  `,
  styles: CHART_TOKENS + `
    .wrap { position: relative; margin-left: 44px; }
    svg { width: 100%; height: 100%; display: block; overflow: visible; }
    .grid { stroke: var(--viz-grid); stroke-width: 1; vector-effect: non-scaling-stroke; }
    .area { fill: var(--viz-series-1); opacity: .08; }
    .line { fill: none; stroke: var(--viz-series-1); stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
    .cross { stroke: var(--viz-text-2); stroke-width: 1; stroke-dasharray: 3 3; }
    .dot { position: absolute; width: 10px; height: 10px; border-radius: 50%; background: var(--viz-series-1);
      box-shadow: 0 0 0 2px var(--viz-surface); transform: translate(-50%, -50%); pointer-events: none; }
    .hits { position: absolute; inset: 0; display: flex; }
    .hits span { flex: 1; }
    .ylab { position: absolute; right: calc(100% + 6px); transform: translateY(-50%); font-size: 11px; color: var(--viz-text-2);
      font-variant-numeric: tabular-nums; }
    .xlabels { display: flex; justify-content: space-between; margin: 6px 0 0 44px; font-size: 11px; color: var(--viz-text-2); }
  `
})
export class LineChartComponent {
  labels = input.required<string[]>();
  values = input.required<number[]>();
  name = input('Value');
  height = input(140);
  fmt = input<(v: number) => string>((v) => v.toLocaleString('en-IN'));
  short = input<(l: string) => string>((l) => l);

  readonly W = 600;
  readonly H = 100;
  hover = signal<number | null>(null);

  private lo = computed(() => {
    if (!this.values().length) { return 0; }
    const min = Math.min(...this.values());
    const max = Math.max(...this.values());
    const pad = Math.max(1, (max - min) * 0.25);
    return Math.max(0, Math.floor(min - pad));
  });
  private hi = computed(() => {
    if (!this.values().length) { return 1; }
    const min = Math.min(...this.values());
    const max = Math.max(...this.values());
    return Math.ceil(max + Math.max(1, (max - min) * 0.25));
  });
  gridLines = computed(() => [this.lo(), Math.round((this.lo() + this.hi()) / 2), this.hi()]);

  x(i: number): number {
    const n = this.values().length;
    return n <= 1 ? this.W / 2 : (this.W * (i + 0.5)) / n;
  }

  y(v: number): number {
    return this.H * (1 - (v - this.lo()) / Math.max(1, this.hi() - this.lo()));
  }

  points = computed(() => this.values().map((v, i) => `${this.x(i)},${this.y(v)}`).join(' '));
  area = computed(() => `${this.x(0)},${this.H} ${this.points()} ${this.x(this.values().length - 1)},${this.H}`);
}

/** Round up to 1/2/2.5/5 × 10^n so gridlines land on readable values. */
function niceCeil(v: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * p >= v) {
      return m * p;
    }
  }
  return 10 * p;
}
