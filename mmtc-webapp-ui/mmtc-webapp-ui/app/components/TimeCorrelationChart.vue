<script setup lang="ts">
import { sub, parseISO } from 'date-fns'
import { eachDayOfInterval, eachWeekOfInterval, eachMonthOfInterval } from 'date-fns'
// import { VisXYContainer, VisLine, VisAxis, VisArea, VisCrosshair, VisTooltip } from '@unovis/vue'
import type { Period, Range } from '~/types'
import {parseIso8601Utc} from "../services/utils";

import VChart, { THEME_KEY } from 'vue-echarts';
import { ref, provide } from 'vue';
import type { TimekeepingTelemetryPoint } from '@/services/mmtc-api';
import * as echarts from 'echarts';
// const formatTime = echarts.time.format;

use([
  CanvasRenderer,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
]);

import { use } from 'echarts/core'
import { PieChart, LineChart, ScatterChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ComposeOption } from 'echarts/core'
import type { PieSeriesOption } from 'echarts/charts'
import type {
  TitleComponentOption,
  TooltipComponentOption,
  LegendComponentOption,
} from 'echarts/components'
import {toYyyyDddHhMm, toYyyyDd, toHhMm} from "@/services/utils";
import {TimeCorrelationTriplet} from "../services/mmtc-api";
import {UnifiedCalendarDateRange} from "../services/utils";

use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  LineChart,
  ScatterChart,
  DataZoomComponent,
  GridComponent,
  CanvasRenderer
])

type EChartsOption = ComposeOption<
  | TitleComponentOption
  | TooltipComponentOption
  | LegendComponentOption
  | PieSeriesOption
>

const timeCorrChart = useTemplateRef<HTMLElement | null>('timeCorrChart')

const cardRef = useTemplateRef<HTMLElement | null>('cardRef')

const props = defineProps<{
  chartTimeSelectionCfg: object
  chartData: object
  range: UnifiedCalendarDateRange
}>()

const emit = defineEmits([
  'time-selection',
])

type DataRecord = {
  date: Date
  amount: number
}

const x = (_: DataRecord, i: number) => i
const y = (d: DataRecord) => d.amount

/*
const formatDate = (date: Date): string => {
  return ({
    daily: format(date, 'd MMM'),
    weekly: format(date, 'd MMM'),
    monthly: format(date, 'MMM yyy')
  })[props.period]
}



const xTicks = (i: number) => {
  if (i === 0 || i === data.value.length - 1 || !data.value[i]) {
    return ''
  }

  return formatDate(data.value[i].date)
}

const template = (d: DataRecord) => `${formatDate(d.date)}: "foobar"`
*/

// --------------------
// Chart configuration
// var formatTime = echarts.time.format;

// var _data = generateData1();

function buildTooltipContent(param) {
  switch(param.seriesId) {
    case 'timeCorrTelemetry':
      // 'ERT/SCLK/SCET/OWLT/SCET Error'
      return param.marker + `Actual TLM: ${param.data.originalTtp.originalFrameSample.ertStr}`
    case 'timeCorrRecords':
      return `Actual rec: ${param.data.originalTriplet.encSclk}`
    case 'timeCorrTelemetryPreview':
      return `Prev TLM: ${param.data.originalTtp.originalFrameSample.ertStr}`
    case 'timeCorrRecordsPreview':
      return `Prev rec: ${param.data.originalTriplet.encSclk}`
  }
}

const option = ref({
  // Choose axis ticks based on UTC time.
  // useUTC: true,
  tooltip:
    {
      show: true,
      trigger: 'axis',
      formatter: (params) => {
        let htmlContents = toYyyyDddHhMm(new Date(params[0].axisValue)) + '<hr/>';

        params.forEach(p => {
          htmlContents += buildTooltipContent(p) + "<br/>";
        })

        return htmlContents;
      }
    },
  /*
  axisPointer: {
    link: [{ xAxisIndex: [0, 1]}]
  },
  title: {
    text: 'Time Correlation Telemetry - Reconstructed SCET Error',
    left: 'center'
  },

   */
  legend: {
    show: true,
    top: '2%',
    left: 'center',
    orient: 'horizontal'
  },
  grid: [
    { top: 80, left: 70, right: 20, height: '60%' },
    { top: '80%', left: 70, right: 20, height: '5%' }
  ],
  xAxis: [
    {
      name: 'SCET/UTC',
      nameLocation: 'middle',
      nameGap: 30,
      type: 'time',
      interval: 1000 * 60 * 30, // 30 minutes
      gridIndex: 0,
      boundaryGap: false,
      axisLabel: {
        showMinLabel: true,
        showMaxLabel: true,
        hideOverlap: true,
        rich: {
          bold: {
            fontWeight: 'bold'
          }
        },
        formatter: (value, index, extra) => {
          // return toYyyyDddHhMm(new Date(value));
          const d = new Date(value);
          // return `<strong>${toYyyyDd(d)}</strong> ${toHhMm(d)}`
          return '{bold|' + toYyyyDd(d) + '} ' + toHhMm(d);
        }
      },
    },
    {
      type: 'time',
      interval: 1000 * 60 * 30, // 30 minutes
      gridIndex: 1,
      boundaryGap: false,
      axisLabel: false
    }
  ],

  yAxis: [
    {
      type: 'value',
      gridIndex: 0,
      name: 'Recon. SCET Error (ms)',
      nameGap: 20
    },
    {
      type: 'value',
      gridIndex: 1,
      name: 'Correlations',
      show: false,
      min: 0,
      max: 1
    }
  ],
  dataZoom: [
    {
      type: 'inside',
      xAxisIndex: [0, 1],
    },
    {
      type: 'slider',
      xAxisIndex: [0, 1],
      showDataShadow: false,
      backgroundColor: '#D9FBE8', // --color-green-200,
      fillerColor: '#B3F5D1', //   --color-green-400
      moveHandleStyle: {
        color: '#007F45' // --color-green-700
      }
    }
  ],
  series: []
});

function updateChartTimeRange() {
  option.value.xAxis[0].min = props.range.beginDate.getTime()
  option.value.xAxis[1].min = props.range.beginDate.getTime()

  option.value.xAxis[0].max = props.range.endDate.getTime()
  option.value.xAxis[1].max = props.range.endDate.getTime()

  option.value.dataZoom[0].startValue = props.range.beginDate.getTime()
  option.value.dataZoom[0].startValue = props.range.beginDate.getTime()

  option.value.dataZoom[0].endValue = props.range.endDate.getTime()
  option.value.dataZoom[0].endValue = props.range.endDate.getTime()
}

watch(() => props.range, (newRange, oldRange) => {
  updateChartTimeRange();
})

watch(() => props.chartData, (newChartData, oldChartData) => {
  const telemetryData: TimekeepingTelemetryPoint[] = newChartData.telemetry;
  const telemetrySeriesData = []

  telemetryData.forEach(ttp => {
    let pointDate = parseIso8601Utc(ttp.scetUtc);
    telemetrySeriesData.push({ name: toYyyyDddHhMm(pointDate), value: [pointDate.getTime(), ttp.scetErrorMs], originalTtp: ttp, itemStyle: { color: '#00A155'}}); // this color is --color-green-600 from main.css
  })

  option.value.series[0] = {
    id: 'timeCorrTelemetry',
    name: 'Time Correlation Tlm Points',
    type: 'line',
    xAxisIndex: 0,
    yAxisIndex: 0,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: {color: '#75EDAE'}, // this is --color-green-300 from main.css
    itemStyle: { color: '#0A5331'},  // this is --color-green-900 from main.css
    data: telemetrySeriesData,
    cursor: 'default',
    tooltip: {
      formatter: (params) => {
        return `Sales: <b>${params.value}</b> units`;
      }
    }
  }

  const correlationData: TimeCorrelationTriplet[] = newChartData.correlations;
  const correlationSeriesData = []

  correlationData.forEach(triplet => {
    const pointDate = parseIso8601Utc(triplet.scetUtc);
    const pointName = 'Name: ' + pointDate;

    correlationSeriesData.push({name: pointName, value: [pointDate.getTime(), 0.5, "foobar"], originalTriplet: triplet });
  })

  option.value.series[1] = {
    id: 'timeCorrRecords',
    name: 'Time Correlation Records',
    type: 'scatter',
    xAxisIndex: 1,
    yAxisIndex: 1,
    symbol: 'triangle',
    symbolSize: 14,
    itemStyle: { color: '#0A5331'},  // this is --color-green-900 from main.css
    data: correlationSeriesData,
    cursor: 'default'
  }

  updateChartTimeRange();

  if (newChartData.previewTelemetry.length > 0) {
    const previewTelemetryData: TimekeepingTelemetryPoint[] = newChartData.previewTelemetry;
    const previewTelemetrySeriesData = []

    previewTelemetryData.forEach(ttp => {
      let pointDate = parseIso8601Utc(ttp.scetUtc);
      previewTelemetrySeriesData.push({ name: toYyyyDddHhMm(pointDate), value: [pointDate.getTime(), ttp.scetErrorMs], originalTtp: ttp});
    })

    option.value.series[2] = {
      id: 'timeCorrTelemetryPreview',
      name: 'Preview Time Correlation Tlm Points',
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: {color: 'purple'},
      itemStyle: { color: 'indigo'},
      data: previewTelemetrySeriesData,
      cursor: 'default'
    }
  } else {
    const indexToRemove = option.value.series.findIndex((elt) => elt['id'] === 'timeCorrTelemetryPreview')
    if (indexToRemove != -1) {
      option.value.series.splice(indexToRemove, 1);
    }
  }

  if (newChartData.previewCorrelations.length > 0) {
    const previewCorrelationData: TimeCorrelationTriplet[] = newChartData.previewCorrelations;
    const previewCorrelationSeriesData = []

    previewCorrelationData.forEach(triplet => {
      const pointDate = parseIso8601Utc(triplet.scetUtc);
      const pointName = 'Name: ' + pointDate;

      previewCorrelationSeriesData.push({name: pointName, value: [pointDate.getTime(), 0.5, "foobar"], originalTriplet: triplet});
    })

    option.value.series[3] = {
      id: 'timeCorrRecordsPreview',
      name: 'Preview Time Correlation Records',
      type: 'scatter',
      xAxisIndex: 1,
      yAxisIndex: 1,
      symbol: 'triangle',
      symbolSize: 14,
      itemStyle: { color: 'indigo'},
      data: previewCorrelationSeriesData,
      cursor: 'default'
    }
  } else {
    const indexToRemove = option.value.series.findIndex((elt) => elt['id'] === 'timeCorrRecordsPreview')
    if (indexToRemove != -1) {
      option.value.series.splice(indexToRemove, 1);
    }
  }
})

async function onClickHandler(event) {
  console.log(event);
  if (props.chartTimeSelectionCfg.selectFrom === 'timeCorrTlm' && event.seriesId === 'timeCorrTelemetry') {
    if (event.data.originalTtp.tdtG > props.chartTimeSelectionCfg.minTdt && event.data.originalTtp.tdtG < props.chartTimeSelectionCfg.maxTdt) {
      emit('time-selection', {tdt: event.data.originalTtp.tdtG, ertStr: event.data.originalTtp.originalFrameSample.ertStr});
    } else {
      console.log('time corr point not within bounds!')
    }
  } else if (props.chartTimeSelectionCfg.selectFrom === 'priorCorrelations' && event.seriesId === 'timeCorrRecords') {
    if (event.data.originalTriplet.tdtG > props.chartTimeSelectionCfg.minTdt && event.data.originalTriplet.tdtG < props.chartTimeSelectionCfg.maxTdt) {
      emit('time-selection', {tdt: event.data.originalTriplet.tdtG, ertStr: undefined});
    } else {
      console.log('triplet not within bounds!')
    }
  }
}

function getSeriesIndexForId(seriesId) {
  const seriesIndex = ['timeCorrTelemetry', 'timeCorrRecords'].indexOf(seriesId);

  if (seriesIndex === -1) {
    throw new Error('Series not found: ' + seriesId);
  }

  return seriesIndex;
}

function configSeriesForSelection(seriesId, originalDataObjKey, isSelecting) {
  const currentZoom = option.value.dataZoom?.map(key => ({
    start: key.start,
    end: key.end,
    startValue: key.startValue,
    endValue: key.endValue,
  }));

  const seriesIndex = getSeriesIndexForId(seriesId);
  option.value.series[seriesIndex].cursor = isSelecting ? 'pointer' : 'default';

  option.value.series[seriesIndex].data.forEach(d => {
    if (isSelecting) {
      if ((d[originalDataObjKey].tdtG > props.chartTimeSelectionCfg.minTdt) && (d[originalDataObjKey].tdtG < props.chartTimeSelectionCfg.maxTdt)){
        if ('itemStyle' in d && 'originalColor' in d['itemStyle']) {
          d['itemStyle']['color'] = d['itemStyle']['originalColor'];
          delete d['itemStyle']['originalColor']
        }
      } else {
        if (! ('itemStyle' in d)) {
          d['itemStyle'] = {}
        }
        d['itemStyle']['originalColor'] = d['itemStyle']['color'];
        d['itemStyle']['color'] = 'gray';
      }
    } else {
      if ('itemStyle' in d && 'originalColor' in d['itemStyle']) {
        d['itemStyle']['color'] = d['itemStyle']['originalColor'];
        delete d['itemStyle']['originalColor']
      }
    }
  })

  if (currentZoom) {
    option.value.dataZoom = currentZoom;
    //chart.setOption({ dataZoom: currentZoom }, false);
  }

  /*
  timeCorrChart.value.setOption({
    series: [
      {
        id: seriesId,
        cursor: cursorAppearance
      }
    ]
  });

   */
}

watch(() => props.chartTimeSelectionCfg, (newVal, oldVal) => {
  if (newVal === oldVal) {
    return;
  }

  switch(props.chartTimeSelectionCfg.selectFrom) {
    case 'timeCorrTlm': {
      configSeriesForSelection('timeCorrTelemetry', 'originalTtp', true);
      break;
    }
    case 'priorCorrelations': {
      configSeriesForSelection('timeCorrRecords', 'originalTriplet', true);
      break;
    }
    case 'none':
      configSeriesForSelection('timeCorrTelemetry', 'originalTtp', false);
      configSeriesForSelection('timeCorrRecords', 'originalTriplet', false);
      break;
    default:
      console.warn('unexpected selection config: ' + props.chartTimeSelectionCfg);
  }
})

</script>

<template>
  <UCard ref="cardRef" :ui="{ root: 'overflow-visible', body: '!px-0 !pt-0 !pb-3' }">
    <VChart
      ref="timeCorrChart"
      class="chart"
      :option="option"
      :autoresize="true"
      @click="onClickHandler"
    />
  </UCard>
</template>

<style scoped>
.chart {
  height: 600px;
}
</style>
