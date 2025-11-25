<script setup lang="ts">
import { add, sub, parseISO, isBefore } from 'date-fns'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Range, ChartData } from '~/types'
import { retrieveOutputProductDefs, retrieveTimekeepingTelemetry } from '@/services/mmtc-api';
import { floorToMidnight } from '@/services/utils';
import {toUtcIso8601WithDiscardedTimezone} from "../services/utils";
import {getTimeCorrelations, getAllTimeCorrelations} from "../services/mmtc-api";
import {TimeCorrelationPreviewResults} from "../services/mmtc-api";
import { today, CalendarDate } from '@internationalized/date'
// import { ref, computed } from 'vue'

//import { CalendarDate, CalendarDateRange } from './CalendarDateRange';
import { UnifiedCalendarDateRange } from "../services/utils";

// TELEMETRY QUERY OPTIONS
const rangeIsInitialized = ref(false);
// this was a shallowref

const doyDateRangePicker = useTemplateRef('doyDateRangePicker');
const dateRange = ref<UnifiedCalendarDateRange>(new UnifiedCalendarDateRange());

// 'view' or 'correlate'
const mode = ref('view')

const chartTimeSelectionCfg = ref({
  selectFrom: 'none',            // timeCorrTlm, priorCorrelations
  selectionDestination: 'none',  // none, target-sample-exact-ert, target-sample-range-start-ert, target-sample-range-stop-ert, prior-correlation-exact-tdt
  minTdt: 0.0,
  maxTdt: Infinity
})

const chartData: ChartData = ref({})

const sclkKernelSelectionChoice = ref('')
const sclkKernelSelectionOptions = ref([])
const correlationConfigPaneOpen = ref(false)
const chartClass = ref('col-span-4')

// template component refs
const newTimeCorrelationConfig = ref(null)
const runHistory = ref(null)

async function newCorrelation() {
  mode.value = 'correlate'
  correlationConfigPaneOpen.value = true
  chartClass.value = 'col-span-3'
  await setSclkKernelToLatest()
}

function closeCorrelation() {
  mode.value = 'view'
  correlationConfigPaneOpen.value = false
  chartClass.value = 'col-span-4'
}

function updateDateRange(newDateRange) {
  dateRange.value = newDateRange;
  rangeIsInitialized.value = true;
}

async function setDefaultDateRange() {
  rangeIsInitialized.value = false;
  const allTimeCorrelations: TimeCorrelationTriplet[] = await getAllTimeCorrelations(sclkKernelSelectionChoice.value);

  // prefer going two correlations back, minus 24h, as the default chart start time
  let rangeStartScetUtcStr;
  if (allTimeCorrelations.length == 1) {
    rangeStartScetUtcStr = allTimeCorrelations[0].scetUtc
  } else {
    rangeStartScetUtcStr = allTimeCorrelations[allTimeCorrelations.length - 2].scetUtc
  }

  const rangeStartScetUtc = parseISO(rangeStartScetUtcStr + 'Z');
  const rangeStartScetDateUtcCldDate = new CalendarDate(rangeStartScetUtc.getUTCFullYear(), rangeStartScetUtc.getUTCMonth() + 1, rangeStartScetUtc.getUTCDate()).subtract({days: 1});

  // default end time is latest time corr + 7 days, or now, whichever is sooner
  const rangeEndUtcBasedOnStartCldDate = rangeStartScetDateUtcCldDate.add({ days: 14 })
  const rangeEndUtcBasedOnNowCldDate = today('UTC');
  let calculatedRangeEndUtcCldDate;
  if (isBefore(rangeEndUtcBasedOnStartCldDate, rangeEndUtcBasedOnNowCldDate)) {
    calculatedRangeEndUtcCldDate = rangeEndUtcBasedOnStartCldDate;
  } else {
    calculatedRangeEndUtcCldDate = rangeEndUtcBasedOnNowCldDate
  }

  doyDateRangePicker.value.setInitialCalendarDates(rangeStartScetDateUtcCldDate, calculatedRangeEndUtcCldDate);
}

onMounted(async () => {
  refreshAll();
})

async function setSclkKernelToLatest() {
  const allDefs = await retrieveOutputProductDefs();
  allDefs.forEach((def) => {
    if (def.name === 'SCLK Kernel') {
      sclkKernelSelectionOptions.value = def.filenames;
      sclkKernelSelectionChoice.value = def.filenames[0];
    }
  })
}

async function refreshAll() {
  await setSclkKernelToLatest();
  await runHistory.value.refresh();
  await setDefaultDateRange();
  await updateChartData();
}

async function updateChartData() {
  if (! rangeIsInitialized.value) {
    return;
  }

  let tmpChartData = {}
  tmpChartData.telemetry = await retrieveTimekeepingTelemetry(toUtcIso8601WithDiscardedTimezone(dateRange.value.beginDate), toUtcIso8601WithDiscardedTimezone(dateRange.value.endDate), sclkKernelSelectionChoice.value);
  tmpChartData.correlations = await getTimeCorrelations(toUtcIso8601WithDiscardedTimezone(dateRange.value.beginDate), toUtcIso8601WithDiscardedTimezone(dateRange.value.endDate), sclkKernelSelectionChoice.value);
  tmpChartData.previewTelemetry = []
  tmpChartData.previewCorrelations = []
  chartData.value = tmpChartData;
}

function handleIncomingTimeSelection(selectionVal) {
  newTimeCorrelationConfig.value.acceptSelectedTime(selectionVal);
}

watch([sclkKernelSelectionChoice, dateRange, rangeIsInitialized], async (newSclkKernelSelectionChoice, oldSclkKernelSelectionChoice) => {
  updateChartData();
})

function changeChoosingState(timeSelectionCfg) {
  chartTimeSelectionCfg.value = timeSelectionCfg;
}

function chartShowCorrelationPreview(timeCorrPreviewResults: TimeCorrelationPreviewResults) {
  // because watched properties only trigger on updating the actual watched reference, not any mutation
  let tmpChartData = {};
  tmpChartData.telemetry = chartData.value.telemetry;
  tmpChartData.correlations = chartData.value.correlations;
  tmpChartData.previewTelemetry = timeCorrPreviewResults.telemetryPoints;
  tmpChartData.previewCorrelations = timeCorrPreviewResults.updatedTriplets;
  chartData.value = tmpChartData;
}

function chartClearCorrelationPreview() {
  // because watched properties only trigger on updating the actual watched reference, not any mutation
  let tmpChartData = {};
  tmpChartData.telemetry = chartData.value.telemetry;
  tmpChartData.correlations = chartData.value.correlations;
  tmpChartData.previewTelemetry = [];
  tmpChartData.previewCorrelations = [];
  chartData.value = tmpChartData;
}

</script>

<template>
  <UDashboardPanel id="home">
    <template #header>
      <UDashboardToolbar class="align-middle" style="height: 72px;">
        <template #left>
          <h2>
            <strong>Time Correlation Telemetry</strong>
          </h2>
          <div>
            <!--HomeDateRangePicker v-model="range"/-->
            <DoyDateRangePicker
              ref="doyDateRangePicker"
              :disabled="mode === 'correlate'"
              @update-date-range="updateDateRange"
            />
            <!--
            Viewing last <USelect v-model="tlmQueryDuration" :items="tlmQueryDurationOptions"/> ending yyyy-doy | TLM loaded through timestamp
            -->
            <USelect v-model="sclkKernelSelectionChoice" :items="sclkKernelSelectionOptions" :disabled="mode === 'correlate'"/>
          </div>
        </template>

        <template #right>
          <span v-if="mode === 'view'">
            <UTooltip text="Start configuring a new correlation">
              <UButton @click="newCorrelation">New correlation</UButton>
            </UTooltip>
          </span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="grid grid-cols-4 gap-6">
        <div :class="chartClass">
          <TimeCorrelationChart
            :chartTimeSelectionCfg="chartTimeSelectionCfg"
            :chartData="chartData"
            :range="dateRange"
            @time-selection="handleIncomingTimeSelection"
          />
        </div>
        <div class="col-span-1" v-if="mode === 'correlate'">
          <UCard ref="cardRef" :ui="{ root: 'overflow-visible', body: 'ml-5 !px-0 !pt-0 !pb-3' }">
            <NewTimeCorrelationConfig
              ref="newTimeCorrelationConfig"
              :range="dateRange"
              @change-choosing-state="changeChoosingState"
              @close-correlation="closeCorrelation"
              @chart-show-correlation-preview="chartShowCorrelationPreview"
              @chart-clear-correlation-preview="chartClearCorrelationPreview"
              @refresh-dashboard="refreshAll"
              />
          </UCard>
        </div>
      </div>
      <RunHistory ref="runHistory"/>
    </template>
  </UDashboardPanel>
</template>
