<script setup lang="ts">
import { eachDayOfInterval, eachWeekOfInterval, eachMonthOfInterval } from 'date-fns'
// import { VisXYContainer, VisLine, VisAxis, VisArea, VisCrosshair, VisTooltip } from '@unovis/vue'
import type { Period, Range } from '~/types'

import VChart, { THEME_KEY } from 'vue-echarts';
import { ref, provide } from 'vue';

import * as echarts from 'echarts';
const formatTime = echarts.time.format;

use([
  CanvasRenderer,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
]);

import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ComposeOption } from 'echarts/core'
import type { PieSeriesOption } from 'echarts/charts'
import type {
  TitleComponentOption,
  TooltipComponentOption,
  LegendComponentOption
} from 'echarts/components'

use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  PieChart,
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
  correlationConfigCompositionState: string
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

var _data = generateData1();

const option = {
  // Choose axis ticks based on UTC time.
  useUTC: true,
  title: {
    text: 'Time Correlation Telemetry SCET - SCET Error',
    left: 'center'
  },
  tooltip: {
    show: true,
    trigger: 'axis'
  },
  grid: {
    left: 50,
    top: 75,
    right: 50,
    bottom: 80
  },
  xAxis: [
    {
      type: 'time',
      interval: 1000 * 60 * 30, // 30 minutes
      axisLabel: {
        showMinLabel: true,
        showMaxLabel: true,
        formatter: (value, index, extra) => {
          if (!extra || !extra.break) {
            // The third parameter is `useUTC: true`.
            return formatTime(value, '{HH}:{mm}', true);
          }
          // Only render the label on break start, but not on break end.
          if (extra.break.type === 'start') {
            return (
              formatTime(extra.break.start, '{HH}:{mm}', true) +
              '/' +
              formatTime(extra.break.end, '{HH}:{mm}', true)
            );
          }
          return '';
        }
      },
    }
  ],
  yAxis: {
    type: 'value',
    min: 'dataMin'
  },
  dataZoom: [
    {
      type: 'inside',
      xAxisIndex: 0
    },
    {
      type: 'slider',
      xAxisIndex: 0
    }
  ],
  series: [
    {
      type: 'line',
      symbol: 'circle',
      symbolSize: 6,
      data: _data.seriesData
    }
  ]
};

/**
 * Generate random data, not relevant to echarts API.
 */
function generateData1() {
  var seriesData = [];
  var time = new Date('2024-04-09T09:30:00Z');
  var endTime = new Date('2024-04-09T15:00:00Z').getTime();
  var breakStart = new Date('2024-04-09T11:30:00Z').getTime();
  var breakEnd = new Date('2024-04-09T13:00:00Z').getTime();

  for (var val = 1669; time.getTime() <= endTime; ) {
    if (time.getTime() <= breakStart || time.getTime() >= breakEnd) {
      val =
        val +
        Math.floor((Math.random() - 0.5 * Math.sin(val / 1000)) * 20 * 100) /
        100;
      val = +val.toFixed(2);
      // seriesData.push([time.getTime(), val]);
      seriesData.push({ value: [time.getTime(), val], itemStyle: { color: 'red'}});
    }
    time.setMinutes(time.getMinutes() + 1);
  }
  return {
    seriesData: seriesData,
    breakStart: breakStart,
    breakEnd: breakEnd
  };
}



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
