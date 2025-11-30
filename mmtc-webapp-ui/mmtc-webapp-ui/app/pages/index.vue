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

const dateRangeChangeLoading = ref(false);
const sclkKernelChangeLoading = ref(false);
const loading = computed(() => {
  // if we're reacting to any change in the chart input params, then we're in a loading state
  // or if we haven't yet initialized the telemetry date range, we're also in a loading state
  return (dateRangeChangeLoading.value || sclkKernelChangeLoading.value ) || (! rangeIsInitialized.value ); // todo this is the cause of the 'hydration completed but contains mismatches' warning when running in dev mode
});

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

  doyDateRangePicker.value.setInitialCalendarDates(rangeStartScetDateUtcCldDate, addToCalDateUpTilToday(rangeStartScetDateUtcCldDate, {days: 14}));

  const quickSelectOptions = [];

  quickSelectOptions.push({
    startCalDate: rangeStartScetDateUtcCldDate,
    endCalDate: addToCalDateUpTilToday(rangeStartScetDateUtcCldDate, {days: 7}),
    displayText: "Last corrs +7d"
  });

  quickSelectOptions.push({
    startCalDate: rangeStartScetDateUtcCldDate,
    endCalDate: addToCalDateUpTilToday(rangeStartScetDateUtcCldDate, {days: 14}),
    displayText: "Last corrs +14d"
  });

  quickSelectOptions.push({
    startCalDate: rangeStartScetDateUtcCldDate,
    endCalDate: addToCalDateUpTilToday(rangeStartScetDateUtcCldDate, {days: 30}),
    displayText: "Last corrs +30d"
  });

  quickSelectOptions.push({
    startCalDate: rangeStartScetDateUtcCldDate,
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Last corrs til now"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 1}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past day"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 7}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past 7 days"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 14}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past 14 days"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 30}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past 30 days"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 90}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past 90 days"
  });

  quickSelectOptions.push({
    startCalDate: (new UnifiedCalendarDateRange()).endCalendarDate.subtract({days: 365}),
    endCalDate: (new UnifiedCalendarDateRange()).endCalendarDate,
    displayText: "Past year"
  });

  doyDateRangePicker.value.setQuickSelectOptions(quickSelectOptions);
}

function addToCalDateUpTilToday(origCalDate, addOpt) {
  const todayCalDate: CalendarDate = today('UTC');
  const newCalDate: CalendarDate = origCalDate.add(addOpt);
  if (newCalDate.compare(todayCalDate) < 0) {
    return newCalDate;
  } else {
    return todayCalDate;
  }
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

  dateRangeChangeLoading.value = true;

  let tmpChartData = {}
  tmpChartData.telemetry = await retrieveTimekeepingTelemetry(toUtcIso8601WithDiscardedTimezone(dateRange.value.beginDate), toUtcIso8601WithDiscardedTimezone(dateRange.value.endDate), sclkKernelSelectionChoice.value);
  tmpChartData.correlations = await getTimeCorrelations(toUtcIso8601WithDiscardedTimezone(dateRange.value.beginDate), toUtcIso8601WithDiscardedTimezone(dateRange.value.endDate), sclkKernelSelectionChoice.value);
  tmpChartData.previewTelemetry = []
  tmpChartData.previewCorrelations = []
  chartData.value = tmpChartData;

  dateRangeChangeLoading.value = false;
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
          <div class="grid grid-cols-3 gap-x-2">
            <!--HomeDateRangePicker v-model="range"/-->
            <div>
              <DoyDateRangePicker
                ref="doyDateRangePicker"
                :disabled="loading || (mode === 'correlate')"
                @update-date-range="updateDateRange"
              />
            </div>
            <!--
            Viewing last <USelect v-model="tlmQueryDuration" :items="tlmQueryDurationOptions"/> ending yyyy-doy | TLM loaded through timestamp
            -->
            <div>
              <USelect v-model="sclkKernelSelectionChoice" :items="sclkKernelSelectionOptions" :disabled="loading || (mode === 'correlate')"/>
            </div>

            <div v-if="!loading" />
            <div v-if="loading">
              <UProgress animation="elastic" class="pt-6" color="secondary"/>
            </div>

            <div>
              <p class="text-xs text-gray-500 text-center">
                Telemetry range (ERT)
              </p>
            </div>
            <div>
              <p class="text-xs text-gray-500 text-center">
                SCLK kernel
              </p>
            </div>

            <div v-if="!loading" />
            <div v-if="loading">
              <p class="text-xs text-gray-500 text-center">
                Loading...
              </p>
            </div>
          </div>
        </template>

        <template #right>
          <div class="grid grid-cols-1">
          <div v-if="mode === 'view'">
            <UTooltip text="Start configuring a new correlation">
              <UButton @click="newCorrelation" :disabled="loading">New correlation</UButton>
            </UTooltip>
          </div>
          </div>
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
      <RunHistory
        ref="runHistory"
        @refresh-dashboard="refreshAll"
      />
    </template>
  </UDashboardPanel>
</template>
