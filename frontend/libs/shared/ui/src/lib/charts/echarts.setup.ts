import * as echarts from 'echarts/core';
import { LineChart, PieChart } from 'echarts/charts';
import { GridComponent, MarkPointComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

/**
 * Registrazione esplicita dei soli moduli ECharts usati dall'app.
 *
 * Oggi ce ne sono due — la curva delle spese e la struttura per categoria —
 * quindi qui vivono le linee e le torte. Le classifiche dei Movimenti restano
 * barre in CSS, senza libreria: lì sono righe di un elenco, non un grafico.
 * Aggiungere un tipo di grafico significa aggiungerlo a questo elenco.
 */
echarts.use([
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  MarkPointComponent,
  CanvasRenderer,
]);

export { echarts };
