import {
  Component,
  ElementRef,
  OnDestroy,
  afterRenderEffect,
  input,
  viewChild,
} from '@angular/core';
import {
  CategoryScale,
  Chart,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Filler,
  Legend,
  Tooltip,
);

export interface ChartSeries {
  label: string;
  data: (number | null)[];
  color: string;
  /** Plot against the right-hand axis. */
  rightAxis?: boolean;
  fill?: boolean;
}

/** Thin wrapper around a Chart.js line chart, rebuilt whenever its inputs change. */
@Component({
  selector: 'app-line-chart',
  template: `<div class="chart-box"><canvas #canvas></canvas></div>`,
  styles: `
    .chart-box {
      position: relative;
      height: 280px;
    }
  `,
})
export class LineChart implements OnDestroy {
  readonly labels = input.required<string[]>();
  readonly series = input.required<ChartSeries[]>();
  /** Suffix appended to the axis ticks, e.g. `%`. */
  readonly unit = input('');
  readonly rightUnit = input('');

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    afterRenderEffect(() => {
      const labels = this.labels();
      const series = this.series();
      const unit = this.unit();
      const rightUnit = this.rightUnit();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, {
        type: 'line',
        data: {
          labels,
          datasets: series.map((s) => ({
            label: s.label,
            data: s.data,
            borderColor: s.color,
            backgroundColor: s.fill ? s.color + '22' : s.color,
            fill: s.fill ?? false,
            yAxisID: s.rightAxis ? 'y1' : 'y',
            borderWidth: 2,
            pointRadius: labels.length > 60 ? 0 : 2,
            tension: 0.2,
            spanGaps: true,
          })),
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          interaction: { mode: 'index', intersect: false },
          plugins: { legend: { display: series.length > 1 } },
          scales: {
            x: { ticks: { maxTicksLimit: 8, maxRotation: 0 }, grid: { display: false } },
            y: { ticks: { callback: (v) => formatTick(v, unit) } },
            y1: {
              display: series.some((s) => s.rightAxis),
              position: 'right',
              grid: { drawOnChartArea: false },
              ticks: { callback: (v) => formatTick(v, rightUnit) },
            },
          },
        },
      });
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}

function formatTick(value: string | number, unit: string): string {
  const n = Number(value);
  return new Intl.NumberFormat('en-US', { maximumSignificantDigits: 6 }).format(n) + unit;
}
