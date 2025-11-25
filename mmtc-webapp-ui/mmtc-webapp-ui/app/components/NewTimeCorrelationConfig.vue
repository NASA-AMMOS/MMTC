<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { DefaultTimeCorrelationConfig, AdditionalSmoothingRecordConfig } from '@/services/mmtc-api';
import { getDefaultCorrelationConfig, runCorrelationPreview, createCorrelation } from '@/services/mmtc-api';
import {toUtcIso8601WithDiscardedTimezone, UnifiedCalendarDateRange} from "../services/utils";
import {TimeCorrelationPreviewResults} from "../services/mmtc-api";

const emit = defineEmits([
  'close-correlation',
  'change-choosing-state',
  'chart-show-correlation-preview',
  'chart-clear-correlation-preview',
  'refresh-dashboard'
])

const props = defineProps<{
  range: UnifiedCalendarDateRange
}>()

// todo update schema
const schema = z.object({
  email: z.email('Invalid email'),
  password: z.string('Password is required').min(8, 'Must be at least 8 characters')
})

type Schema = z.output<typeof schema>

// 'range' or 'exact'
const targetSampleSelectionType = ref('range');

const newCorrelationMinTdt = ref(0.0);
const newCorrelationMinLookbackHours = ref(0.0);
const newCorrelationMaxLookbackHours = ref(0.0);

const targetSampleExactTdt = ref(0.0);
const targetSampleRangeStartTdt = ref(0.0);
const targetSampleRangeStopTdt = ref(Infinity);

const priorLookbackMinTdt = ref(0.0);
const priorLookbackMaxTdt = ref(Infinity);

const state = reactive<Partial<Schema>>({
  targetSampleRangeStartErt: undefined,
  targetSampleRangeStopErt: undefined,
  targetSampleExactErt: undefined,
  priorCorrelationExactTdt: undefined,
  testModeOwltEnabled: undefined,
  testModeOwltSec: undefined,
  clockChangeRateAssignedValue: undefined,
  clockChangeRateModeOverride: undefined,
  additionalSmoothingRecordConfigOverride: { enabled: false, coarseSclkTickDuration: 0 },
  isDisableContactFilter: undefined,
  isCreateUplinkCmdFile: undefined,
  dryRunConfig: { mode: 'NOT_DRY_RUN', sclkKernelOutputPath: null }
})

const defaultTargetSampleErtPlaceholder = 'YYYY-DDDTHH:MM:SS[.ssssss]'
const defaultPriorCorrelationTdtPlaceholder = 'Automatic'

const targetSampleExactErtPlaceholder = ref(defaultTargetSampleErtPlaceholder)
const targetSampleRangeStartErtPlaceholder = ref(defaultTargetSampleErtPlaceholder)
const targetSampleRangeStopErtPlaceholder = ref(defaultTargetSampleErtPlaceholder)
const priorCorrelationTdtPlaceholder = ref(defaultPriorCorrelationTdtPlaceholder)

const defaultCorrelationConfig = ref({})

const correlationPreviewResults = ref({})

// keep prior lookback range up to date
watch(targetSampleExactTdt, () => {
  priorLookbackMinTdt.value = targetSampleExactTdt.value - newCorrelationMaxLookbackHours.value;
  priorLookbackMaxTdt.value = targetSampleExactTdt.value - newCorrelationMinLookbackHours.value;
})

watch(targetSampleRangeStartTdt, () => {
  // nothing?
})

watch(targetSampleRangeStopTdt, () => {
  priorLookbackMinTdt.value = targetSampleRangeStartTdt.value - newCorrelationMaxLookbackHours.value;
  priorLookbackMaxTdt.value = targetSampleRangeStopTdt.value - newCorrelationMinLookbackHours.value;
})

// preview information
const previewTabItems = [
  {
    label: ' ',
    icon: 'i-lucide-info',
    slot: 'overview'
  },
  {
    label: ' ',
    icon: 'i-lucide-target',
    slot: 'target'
  },
  {
    label: ' ',
    icon: 'i-lucide-list',
    slot: 'triplets'
  }
]

const toast = useToast()

defineExpose({ acceptSelectedTime });

const clockChangeRateModeChoices = ['COMPUTE_PREDICT', 'COMPUTE_INTERPOLATE', 'ASSIGN', 'NO_DRIFT']

const additionalCorrelationOptionItems = ref<CheckboxGroupItem[]>([
  {
    label: 'Insert additional smoothing record',
    description: 'Prepends an additional triplet ahead of the actual telemetry-based triplet to ensure SCLK-SCET continuity',
    value: 'insertAdditionalSmoothingRecord'
  },
  {
    label: 'Disable Contact Filter',
    description: "Disables the Contact Filter, even if it's enabled in the configuration file",
    value: 'disableContactFilter'
  },
  {
    label: 'Create Uplink Command File',
    description: 'Create an Uplink Command File',
    value: 'createUplinkCommandFile'
  },
  {
    label: 'Test Mode OWLT',
    description: 'Set a specific one-way light time for analysis or testing purposes, including when ephemerides are not available',
    value: 'testModeOwltEnabled'
  }
])

const additionalCorrelationOptionsModel = ref([])

const timeSelectionCfg = ref({
  selectFrom: 'none',            // timeCorrTlm, priorCorrelations
  selectionDestination: 'none',  // none, target-sample-exact-ert, target-sample-range-start-ert, target-sample-range-stop-ert, prior-correlation-exact-tdt
  minTdt: 0.0,
  maxTdt: Infinity
})

// composing / previewing
const timeCorrelationConfigState = ref('composing')

function cancelCorrelation() {
  emit('close-correlation');
}

async function previewCorrelation() {
  timeCorrelationConfigState.value = 'previewing'
  state['beginTime'] = toUtcIso8601WithDiscardedTimezone(props.range.beginDate)
  state['endTime'] = toUtcIso8601WithDiscardedTimezone(props.range.endDate)
  const previewResults: TimeCorrelationPreviewResults = await runCorrelationPreview(state);
  delete state['beginTime']
  delete state['endTime']

  correlationPreviewResults.value = previewResults.correlationResults;
  emit('chart-show-correlation-preview', previewResults);
}

function goBackToComposing() {
  timeCorrelationConfigState.value = 'composing'
  emit('chart-clear-correlation-preview')
}

async function commitCorrelation() {
  state.testModeOwltEnabled                             = additionalCorrelationOptionsModel.includes('testModeOwltEnabled');
  state.additionalSmoothingRecordConfigOverride.enabled = additionalCorrelationOptionsModel.includes('insertAdditionalSmoothingRecord');
  state.isDisableContactFilter                          = additionalCorrelationOptionsModel.includes('disableContactFilter');
  state.isCreateUplinkCmdFile                          = additionalCorrelationOptionsModel.includes('createUplinkCommandFile');

  const results: TimeCorrelationResults = await createCorrelation(state);
  emit('close-correlation');
  emit('chart-clear-correlation-preview')
  emit('refresh-dashboard')
}

function resetTimeSelectionCfg () {
  timeSelectionCfg.value = {
    selectFrom: 'none',
    selectionDestination: 'none',
    minTdt: 0.0,
    maxTdt: Infinity
  }
}

function toggleChoosingTargetSampleExactErt() {
  if (timeSelectionCfg.value.selectionDestination === 'none') {
    timeSelectionCfg.value = {
      selectFrom: 'timeCorrTlm',
      selectionDestination: 'target-sample-exact-ert',
      minTdt: newCorrelationMinTdt.value,
      maxTdt: Infinity
    }

    targetSampleExactErtPlaceholder.value = 'Click a point on the chart';
  } else if (timeSelectionCfg.value.selectionDestination == 'target-sample-exact-ert') {
    resetTimeSelectionCfg();
    targetSampleExactErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
  }
}

function toggleChoosingTargetSampleRangeStartErt() {
  if (timeSelectionCfg.value.selectionDestination === 'none') {
    timeSelectionCfg.value = {
      selectFrom: 'timeCorrTlm',
      selectionDestination: 'target-sample-range-start-ert',
      minTdt: newCorrelationMinTdt.value,
      maxTdt: Infinity
    }

    targetSampleRangeStartErtPlaceholder.value = 'Click a point on the chart';
  } else if (timeSelectionCfg.value.selectionDestination == 'target-sample-range-start-ert') {
    resetTimeSelectionCfg();
    targetSampleRangeStartErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
  }
}

function toggleChoosingTargetSampleRangeStopErt() {
  if (timeSelectionCfg.value.selectionDestination === 'none') {
    timeSelectionCfg.value = {
      selectFrom: 'timeCorrTlm',
      selectionDestination: 'target-sample-range-stop-ert',
      minTdt: targetSampleRangeStartTdt.value,
      maxTdt: Infinity
    }

    targetSampleRangeStopErtPlaceholder.value = 'Click a point on the chart';
  } else if (timeSelectionCfg.value.selectionDestination == 'target-sample-range-stop-ert') {
    resetTimeSelectionCfg();
    targetSampleRangeStopErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
  }
}

function toggleChoosingPriorCorrelationTdt() {
  if (timeSelectionCfg.value.selectionDestination === 'none') {
    timeSelectionCfg.value = {
      selectFrom: 'priorCorrelations',
      selectionDestination: 'prior-correlation-exact-tdt',
      minTdt: priorLookbackMinTdt.value,
      maxTdt: priorLookbackMaxTdt.value
    }

    priorCorrelationTdtPlaceholder.value = 'Click a correlation on the chart';
  } else if (timeSelectionCfg.value.selectionDestination === 'prior-correlation-exact-tdt') {
    resetTimeSelectionCfg();
    priorCorrelationTdtPlaceholder.value = defaultPriorCorrelationTdtPlaceholder;
  }
}

watch(timeSelectionCfg, () => {
  emit('change-choosing-state', timeSelectionCfg.value);
})

function acceptSelectedTime(selection) {
  switch(timeSelectionCfg.value.selectionDestination) {
    case 'target-sample-exact-ert':
      state.targetSampleExactErt = selection.ertStr;
      targetSampleExactTdt.value = selection.tdt;
      break;
    case 'target-sample-range-start-ert':
      state.targetSampleRangeStartErt = selection.ertStr;
      targetSampleRangeStartTdt.value = selection.tdt;
      break;
    case 'target-sample-range-stop-ert':
      state.targetSampleRangeStopErt = selection.ertStr;
      targetSampleRangeStopTdt.value = selection.tdt;
      break;
    case 'prior-correlation-exact-tdt':
      state.priorCorrelationExactTdt = selection.tdt;
      break;
    default:
      console.warn('unexpected time str: ' + selection);
  }

  resetTimeSelectionCfg();
}

watch(additionalCorrelationOptionsModel, (newVal, oldVal) => {
  // console.log(additionalCorrelationOptionsModel.value);
})

onMounted(async () => {
  defaultCorrelationConfig.value = await getDefaultCorrelationConfig();

  if (defaultCorrelationConfig.value.samplesPerSet === 1) {
    targetSampleSelectionType.value = 'exact'
  } else {
    targetSampleSelectionType.value = 'range'
  }

  state.clockChangeRateModeOverride = defaultCorrelationConfig.value.clockChangeRateModeOverride;
  state.additionalSmoothingRecordConfigOverride = defaultCorrelationConfig.value.additionalSmoothingRecordConfigOverride;

  newCorrelationMinTdt.value = defaultCorrelationConfig.value.newCorrelationMinTdt;
  newCorrelationMinLookbackHours.value = defaultCorrelationConfig.value.predictedClkRateMinLookbackHours;
  newCorrelationMaxLookbackHours.value = defaultCorrelationConfig.value.predictedClkRateMaxLookbackHours;
})

</script>

<template>
  <!-- @submit="onSubmit" -->
  <span v-if="timeCorrelationConfigState === 'composing'">
    <UForm :schema="schema" :state="state" class="space-y-4 pr-5 pt-5" style="min-height: 600px;">
      <UFormField label="Target sample (ERT)" name="targetSampleExactErt" size="sm" v-if="targetSampleSelectionType === 'exact'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
          <UInput v-model="state.targetSampleExactErt" :placeholder="targetSampleExactErtPlaceholder" class="w-full" :disabled="timeSelectionCfg.selectionDestination != 'none'"/>
          </div>
          <div class="col-span-1">
            <UButton @click="toggleChoosingTargetSampleExactErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'target-sample-exact-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-exact-ert'].includes(timeSelectionCfg.selectionDestination))"/>
          </div>
        </div>
      </UFormField>

      <UFormField label="Target sample range begin (ERT)" name="targetSampleRangeStartErt" size="sm" v-if="targetSampleSelectionType === 'range'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.targetSampleRangeStartErt" :placeholder="targetSampleRangeStartErtPlaceholder" class="w-full" :disabled="timeSelectionCfg.selectionDestination != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingTargetSampleRangeStartErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'target-sample-range-start-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-range-start-ert'].includes(timeSelectionCfg.selectionDestination))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <UFormField label="Target sample range end (ERT)" name="targetSampleRangeStopErt" size="sm" v-if="targetSampleSelectionType === 'range'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.targetSampleRangeStopErt" :placeholder="targetSampleRangeStopErtPlaceholder" class="w-full" :disabled="timeSelectionCfg.selectionDestination != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingTargetSampleRangeStopErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'target-sample-range-stop-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-range-stop-ert'].includes(timeSelectionCfg.selectionDestination))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <UFormField label="Prior correlation for basis (TDT)" name="priorCorrelationExactTdt"  size="sm">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.priorCorrelationExactTdt" :placeholder="priorCorrelationTdtPlaceholder" class="w-full"  :disabled="timeSelectionCfg.selectionDestination != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingPriorCorrelationTdt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'prior-correlation-exact-tdt' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'prior-correlation-exact-tdt'].includes(timeSelectionCfg.selectionDestination))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <UFormField label="Clock change rate mode" name="clockChangeRateMode" size="sm">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <USelect v-model="state.clockChangeRateModeOverride" :items="clockChangeRateModeChoices" class="w-full"/>
          </div>
          <div class="col-span-1" v-if="state.clockChangeRateModeOverride === 'assign'">
            <UInput v-model="state.specifiedClockChangeRateToAssign" class="w-full"/>
          </div>
        </div>
      </UFormField>


      <UCheckboxGroup v-model="additionalCorrelationOptionsModel" :items="additionalCorrelationOptionItems" size="sm" />

      <UFormField label="Smoothing record duration (SCLK ticks)" name="testModeOwltSec" :style="{visibility: additionalCorrelationOptionsModel.includes('insertAdditionalSmoothingRecord') ? 'visible' : 'hidden'}" size="sm">
        <UInput v-model="state.additionalSmoothingRecordConfigOverride.coarseSclkTickDuration" class="w-full"/>
      </UFormField>

      <UFormField label="Test OWLT (sec)" name="testModeOwltSec" :style="{visibility: additionalCorrelationOptionsModel.includes('testModeOwltEnabled') ? 'visible' : 'hidden'}" size="sm">
        <UInput v-model="state.testModeOwltSec" class="w-full"/>
      </UFormField>

      <div class="pt-5">
        <UTooltip text="Cancel correlation configuration">
          <UButton @click="cancelCorrelation" color="neutral" variant="outline">
            Cancel
          </UButton>
        </UTooltip>

        <UTooltip text="View the results of the new correlation run without committing the changes to output products">
          <UButton @click="previewCorrelation" class="float-right">
            Preview
          </UButton>
        </UTooltip>
      </div>
    </UForm>
  </span>

  <div v-if="timeCorrelationConfigState === 'previewing'" class="pr-5 pt-5">
    <div>
      <UTabs
        :items="previewTabItems"
        size="sm"
      >
        <template #overview>
          Overview content
        </template>
        <template #target>
          Target content
        </template>
        <template #triplets>
          Triplet content
        </template>
      </UTabs>
    </div>
      <div class="pt-5" v-if="timeCorrelationConfigState === 'previewing'">
        <UTooltip text="Return to correlation configuration">
          <UButton @click="goBackToComposing" color="neutral" variant="outline">
            Back
          </UButton>
        </UTooltip>

        <UTooltip text="Commit the correlation, producing all related output products">
          <UButton @click="commitCorrelation" class="float-right">
            Commit
          </UButton>
        </UTooltip>
      </div>
  </div>
</template>

<!--
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
          -->
