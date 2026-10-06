import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

/**
 * Wrapper attorno a ECharts.
 *
 * Isola i componenti dalla libreria: se in futuro cambiasse il motore grafico,
 * l'unico file da toccare è questo.
 */
@Component({
  selector: 'app-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgxEchartsDirective],
  host: {
    class: 'relative block w-full',
    '[class.flex-1]': 'fill()',
    '[style.height.px]': 'fill() ? null : height()',
    '[style.min-height.px]': 'height()',
  },
  // Il canvas sta in assoluto perché con `fill` l'altezza la decide il
  // contenitore: un figlio nel flusso, col suo canvas già dimensionato, gli
  // impedirebbe di restringersi.
  template: `
    <div
      echarts
      class="absolute inset-0"
      role="img"
      [attr.aria-label]="description()"
      [options]="options()"
      [autoResize]="true"
    ></div>
  `,
})
export class Chart {
  readonly options = input.required<EChartsCoreOption>();
  readonly height = input(240);
  /**
   * Prende l'altezza dal contenitore invece che da `height`, che resta il
   * minimo: serve a un grafico che deve pareggiare la card accanto.
   */
  readonly fill = input(false);
  readonly description = input.required<string>();
}
