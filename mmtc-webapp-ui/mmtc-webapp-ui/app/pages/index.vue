<script setup lang="ts">
import { add, sub, parseISO, isBefore } from 'date-fns'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Range, ChartData } from '~/types'
import { retrieveOutputProductDefs, retrieveTimekeepingTelemetry } from '@/services/mmtc-api';
import { floorToMidnight } from '@/services/utils';
import {toIso8601WithDiscardedTimezone} from "../services/utils";
import {getTimeCorrelations, getAllTimeCorrelations} from "../services/mmtc-api";
import {TimeCorrelationPreviewResults} from "../services/mmtc-api";

// TELEMETRY QUERY OPTIONS
const rangeIsInitialized = ref(false);
// this was a shallowref
const range = ref<Range>({
  start: sub(new Date(), { days: 1 }),
  end: new Date()
})
const sclkKernelSelectionChoice = ref('')
const sclkKernelSelectionOptions = ref([])

// 'view' or 'correlate'
const mode = ref('view')
const chartSelectionMode = ref('none')

// none -> chooseTarget -> choosePrior -> preview -> none
const correlationConfigCompositionState = ref('none')
const chartData: ChartData = ref({})
const correlationConfigPaneOpen = ref(false)
const chartClass = ref('col-span-4')

// template component refs
const newTimeCorrelationConfig = ref(null)
const runHistory = ref(null)

function newCorrelation() {
  mode.value = 'correlate'
  correlationConfigPaneOpen.value = true
  chartClass.value = 'col-span-3'
  correlationConfigCompositionState.value = 'choose-target'
}

function closeCorrelation() {
  mode.value = 'view'
  correlationConfigPaneOpen.value = false
  chartClass.value = 'col-span-4'
  correlationConfigCompositionState.value = 'none'
}

async function setDefaultChartRange() {
  console.log('setDefaultChartRange');
  const allTimeCorrelations: TimeCorrelationTriplet[] = await getAllTimeCorrelations(sclkKernelSelectionChoice.value);

  // prefer going two correlations back, minus 24h, as the default chart start time
  let rangeStartScetUtcStr;
  if (allTimeCorrelations.length == 1) {
    rangeStartScetUtcStr = allTimeCorrelations[0].scetUtc
  } else {
    rangeStartScetUtcStr = allTimeCorrelations[allTimeCorrelations.length - 2].scetUtc
  }

  const newRangeStartUtc = sub(floorToMidnight(parseISO(rangeStartScetUtcStr)), { days: 1 });

  // default end time is latest time corr + 14 days, or now, whichever is sooner
  const newRangeEndUtcBasedOnStart = floorToMidnight(add(newRangeStartUtc, { days: 14 }));
  const newRangeEndUtcBasedOnNow = floorToMidnight(new Date());
  let newRangeEndUtc;
  if (isBefore(newRangeEndUtcBasedOnStart, newRangeEndUtcBasedOnNow)) {
    newRangeEndUtc = newRangeEndUtcBasedOnStart;
  } else {
    newRangeEndUtc = newRangeEndUtcBasedOnNow
  }

  // 'round' both dates back to the start of the day
  range.value.start = newRangeStartUtc;
  range.value.end = newRangeEndUtc;
  rangeIsInitialized.value = true;
}

onMounted(async () => {
  refreshAll();
})

async function refreshAll() {
  const allDefs = await retrieveOutputProductDefs();
  allDefs.forEach((def) => {
    if (def.name === 'SCLK Kernel') {
      sclkKernelSelectionOptions.value = def.filenames;
      sclkKernelSelectionChoice.value = def.filenames[0];
    }
  })

  await runHistory.value.refresh();
  await setDefaultChartRange();
  await updateChartData();
}

async function updateChartData() {
  if (! rangeIsInitialized.value) {
    return;
  }

  let tmpChartData = {}
  tmpChartData.telemetry = await retrieveTimekeepingTelemetry(toIso8601WithDiscardedTimezone(range.value.start), toIso8601WithDiscardedTimezone(range.value.end), sclkKernelSelectionChoice.value);
  tmpChartData.correlations = await getTimeCorrelations(toIso8601WithDiscardedTimezone(range.value.start), toIso8601WithDiscardedTimezone(range.value.end), sclkKernelSelectionChoice.value);
  tmpChartData.previewTelemetry = []
  tmpChartData.previewCorrelations = []
  chartData.value = tmpChartData;
}

function handleIncomingTimeSelection(selectedErt) {
  chartSelectionMode.value = 'none'
  newTimeCorrelationConfig.value.acceptSelectedTime(selectedErt);
}

watch([sclkKernelSelectionChoice, range], async (newSclkKernelSelectionChoice, oldSclkKernelSelectionChoice) => {
  updateChartData();
})

function startChoosingTargetSampleErt() {
  chartSelectionMode.value = 'choosing-target-sample-ert';
}

function startChoosingPriorCorrelationTdt() {
  chartSelectionMode.value = 'choosing-prior-correlation-tdt';
}

function cancelChoosing() {
  chartSelectionMode.value = 'none';
}

function chartShowPreviewCorrelationTelemetry(timeCorrPreviewResults: TimeCorrelationPreviewResults) {
  // because watched properties only trigger on updating the actual watched reference, not any mutation
  let tmpChartData = {};
  tmpChartData.telemetry = chartData.value.telemetry;
  tmpChartData.correlations = chartData.value.correlations;
  tmpChartData.previewTelemetry = timeCorrPreviewResults.telemetryPoints;
  tmpChartData.previewCorrelations = timeCorrPreviewResults.updatedTriplets;
  chartData.value = tmpChartData;
}

function chartClearPreviewCorrelationTelemetry(previewTimeCorrTlm: TimekeepingTelemetryPoint[]) {
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
            <HomeDateRangePicker v-model="range"/>
            <!--
            Viewing last <USelect v-model="tlmQueryDuration" :items="tlmQueryDurationOptions"/> ending yyyy-doy | TLM loaded through timestamp
            -->
            <USelect v-model="sclkKernelSelectionChoice" :items="sclkKernelSelectionOptions"/>
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
            :mode="mode"
            :selectionMode="chartSelectionMode"
            :correlationConfigCompositionState="correlationConfigCompositionState"
            :chartData="chartData"
            :range="range"
            @time-selection="handleIncomingTimeSelection"
          />
        </div>
        <div class="col-span-1" v-if="mode === 'correlate'">
          <UCard ref="cardRef" :ui="{ root: 'overflow-visible', body: 'ml-5 !px-0 !pt-0 !pb-3' }">
            <NewTimeCorrelationConfig
              ref="newTimeCorrelationConfig"
              :chartRange="range"
              @start-choosing-target-sample-ert="startChoosingTargetSampleErt"
              @start-choosing-prior-correlation-tdt="startChoosingPriorCorrelationTdt"
              @close-correlation="closeCorrelation"
              @cancel-choosing="cancelChoosing"
              @chart-show-preview-correlation-telemetry="chartShowPreviewCorrelationTelemetry"
              @chart-clear-preview-correlation-telemetry="chartClearPreviewCorrelationTelemetry"
              @refresh-dashboard="refreshAll"
              />
          </UCard>
        </div>
      </div>
      <RunHistory ref="runHistory"/>
    </template>
  </UDashboardPanel>
</template>
