<script setup lang="ts">
import { sub } from 'date-fns'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Period, Range } from '~/types'
import { retrieveOutputProductDefs } from '@/services/mmtc-api';

// TELEMETRY QUERY OPTIONS
const range = shallowRef<Range>({
  start: sub(new Date(), { days: 30 }),
  end: new Date()
})
const sclkKernelSelectionChoice = ref('')
const sclkKernelSelectionOptions = ref([])

// 'view' or 'correlate'
const mode = ref('view')

// none -> chooseTarget -> choosePrior -> preview -> none
const correlationConfigCompositionState = ref('none')

const correlationConfigPaneOpen = ref(false)
const chartClass = ref('col-span-4')

function newCorrelation() {
  mode.value = 'correlate'
  correlationConfigPaneOpen.value = true
  chartClass.value = 'col-span-3'
  correlationConfigCompositionState.value = 'choose-target'
}

function commitCorrelation() {
  mode.value = 'view'
  correlationConfigPaneOpen.value = false
  chartClass.value = 'col-span-4'
  correlationConfigCompositionState.value = 'none'
}

function cancelCorrelation() {
  mode.value = 'view'
  correlationConfigPaneOpen.value = false
  chartClass.value = 'col-span-4'
  correlationConfigCompositionState.value = 'none'
}

onMounted(async () => {
  const allDefs = await retrieveOutputProductDefs();
  allDefs.forEach((def) => {
    if (def.name === 'SCLK Kernel') {
      sclkKernelSelectionOptions.value = def.filenames;
      sclkKernelSelectionChoice.value = def.filenames[0];
    }
  })
})

async function updateChartData() {
  console.log("update chart data");
}

watch([sclkKernelSelectionChoice], async (newSclkKernelSelectionChoice, oldSclkKernelSelectionChoice) => {
  updateChartData();
})

</script>

<template>
  <UDashboardPanel id="home">
    <template #header>
      <UDashboardToolbar class="mt-5.5 align-middle">
        <template #left>
          <span>
            <HomeDateRangePicker v-model="range"/>
            <!--
            Viewing last <USelect v-model="tlmQueryDuration" :items="tlmQueryDurationOptions"/> ending yyyy-doy | TLM loaded through timestamp
            -->
            <USelect v-model="sclkKernelSelectionChoice" :items="sclkKernelSelectionOptions"/>
          </span>
        </template>

        <template #right>
          <span v-if="mode === 'view'">
            <UButton @click="newCorrelation">New correlation</UButton>
          </span>
          <span v-if="mode === 'correlate'">

            <UButton @click="cancelCorrelation">Cancel</UButton>

            <UButton @click="commitCorrelation">Commit</UButton>
          </span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="grid grid-cols-4 gap-6">
        <div :class="chartClass">
          <TimeCorrelationChart :mode="mode" :correlationConfigCompositionState="correlationConfigCompositionState" />
        </div>
        <div class="col-span-1" v-if="mode === 'correlate'">
          <UCard ref="cardRef" :ui="{ root: 'overflow-visible', body: 'ml-5 !px-0 !pt-0 !pb-3' }">
            <div class="grid grid-cols-2">
              <div class="col-span-1">Target sample</div>
              <div class="col-span-1">FOOBAR</div>
              <div class="col-span-1">Prior basis triplet</div>
              <div class="col-span-1">FOOBAR</div>
              <div class="col-span-1">Clock change rate mode</div>
              <div class="col-span-1">FOOBAR</div>
              <div class="col-span-1">Overrides</div>
              <div class="col-span-1">OWLT, Contact Filter, Uplink Command File</div>
            </div>
          </UCard>
        </div>
      </div>
      <RunHistory :range="range"  />
    </template>
  </UDashboardPanel>
</template>
