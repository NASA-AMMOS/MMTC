<script setup lang="ts">
import { sub, parseISO } from 'date-fns'
import { eachDayOfInterval, eachWeekOfInterval, eachMonthOfInterval } from 'date-fns'
// import { VisXYContainer, VisLine, VisAxis, VisArea, VisCrosshair, VisTooltip } from '@unovis/vue'
import type { Period, Range } from '~/types'

import VChart, { THEME_KEY } from 'vue-echarts';
import { ref, provide } from 'vue';
import { TimekeepingTelemetryPoint } from '@/services/mmtc-api';
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
import {toYyyDddHhMm} from "@/services/utils";
import {TimeCorrelationTriplet} from "../services/mmtc-api";

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
  mode: string
  chartData: object
  correlationConfigCompositionState: string
  range: object
}>()

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

const option = ref({
  // Choose axis ticks based on UTC time.
  useUTC: true,

  tooltip:
    {
      show: true,
      trigger: 'axis'
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
  grid: [
    { top: 40, left: 70, right: 20, height: '60%' },
    { top: '80%', left: 70, right: 20, height: '5%' }
  ],
  xAxis: [
    {
      type: 'time',
      interval: 1000 * 60 * 30, // 30 minutes
      gridIndex: 0,
      boundaryGap: false,
      axisLabel: {
        showMinLabel: true,
        showMaxLabel: true,
        hideOverlap: true,
        formatter: (value, index, extra) => {
          return toYyyDddHhMm(new Date(value));
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
      splitLine: {show: true}
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
      xAxisIndex: [0, 1]
    },
    {
      type: 'slider',
      xAxisIndex: [0, 1],
      showDataShadow: false,
      backgroundColor: '#D9FBE8', // --color-green-200
    }
  ],
  series: []
});
/*
formatter: params => {
  const { name, value } = params;
  return `${name} - ${value}`
}

 */
watch(() => props.chartData, async (newChartData, oldChartData) => {
  const telemetryData: TimekeepingTelemetryPoint[] = newChartData.telemetry;
  const telemetrySeriesData = []

  telemetryData.forEach(ttp => {
    let pointDate = parseISO(ttp.scetUtc);
    telemetrySeriesData.push({ name: toYyyDddHhMm(pointDate), value: [pointDate.getTime(), ttp.scetErrorMs], itemStyle: { color: '#00A155'}}); // this color is --color-green-600 from main.css
  })

  option.value.series[0] = {
    name: 'SCET Error (ms)',
    type: 'line',
    xAxisIndex: 0,
    yAxisIndex: 0,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: {color: '#75EDAE'}, // this is --color-green-300 from main.css
    data: telemetrySeriesData
  }

  const correlationData: TimeCorrelationTriplet[] = newChartData.correlations;
  const correlationSeriesData = []

  correlationData.forEach(corr => {
    let pointDate = parseISO(corr.scetUtc);
    // correlationSeriesData.push({ name: toYyyDddHhMm(pointDate), value: [pointDate.getTime(), 0], itemStyle: { color: 'red'}});
    // correlationSeriesData.push([pointDate.getTime(), 0.5, "foobar"]);
    correlationSeriesData.push({ value: [pointDate.getTime(), 0.5, "foobar"], itemStyle: { color: '#0A5331'}}); // this is --color-green-900 from main.css
  })

  option.value.series[1] = {
    name: 'Time Correlation Records',
    type: 'scatter',
    xAxisIndex: 1,
    yAxisIndex: 1,
    symbol: 'triangle',
    symbolSize: 10,
    data: correlationSeriesData
  }

  option.value.xAxis[0].min = props.range.start.getTime()
  option.value.xAxis[1].min = props.range.start.getTime()

  option.value.xAxis[0].max = props.range.end.getTime()
  option.value.xAxis[1].max = props.range.end.getTime()

  option.value.dataZoom[0].startValue = props.range.start.getTime()
  option.value.dataZoom[0].startValue = props.range.start.getTime()

  option.value.dataZoom[0].endValue = props.range.end.getTime()
  option.value.dataZoom[0].endValue = props.range.end.getTime()
})

/*

import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { PieChart } from "echarts/charts";
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
} from "echarts/components";
import VChart, { THEME_KEY } from "vue-echarts";
import { ref, provide } from "vue";

use([
  CanvasRenderer,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
]);

provide(THEME_KEY, "dark");

const option = ref({
  title: {
    text: "Traffic Sources",
    left: "center",
  },
  tooltip: {
    trigger: "item",
    formatter: "{a} <br/>{b} : {c} ({d}%)",
  },
  legend: {
    orient: "vertical",
    left: "left",
    data: ["Direct", "Email", "Ad Networks", "Video Ads", "Search Engines"],
  },
  series: [
    {
      name: "Traffic Sources",
      type: "pie",
      radius: "55%",
      center: ["50%", "60%"],
      data: [
        { value: 335, name: "Direct" },
        { value: 310, name: "Email" },
        { value: 234, name: "Ad Networks" },
        { value: 135, name: "Video Ads" },
        { value: 1548, name: "Search Engines" },
      ],
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: "rgba(0, 0, 0, 0.5)",
        },
      },
    },
  ],
});

 */

async function onClickHandler(event) {
  console.log('onClickHandler')
  console.log(event);
}

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
