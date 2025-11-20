<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { DefaultTimeCorrelationConfig, AdditionalSmoothingRecordConfig } from '@/services/mmtc-api';
import { getDefaultCorrelationConfig, runCorrelationPreview, createCorrelation } from '@/services/mmtc-api';
import {toIso8601WithDiscardedTimezone} from "../services/utils";
import {TimeCorrelationPreviewResults} from "../services/mmtc-api";

const emit = defineEmits([
  'close-correlation',
  'start-choosing-target-sample-ert',
  'start-choosing-prior-correlation-tdt',
  'cancel-choosing',
  'chart-show-preview-correlation-telemetry',
  'chart-clear-preview-correlation-telemetry',
  'refresh-dashboard'
])

const props = defineProps<{
  chartRange: object
}>()

// todo update schema
const schema = z.object({
  email: z.email('Invalid email'),
  password: z.string('Password is required').min(8, 'Must be at least 8 characters')
})

type Schema = z.output<typeof schema>

// 'range' or 'exact'
const targetSampleSelectionType = ref('range')

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

const toast = useToast()
async function onSubmit(event: FormSubmitEvent<Schema>) {
  // incorporate checkbox information
  state.testModeOwltEnabled                             = additionalCorrelationOptionsModel.includes('testModeOwltEnabled');
  state.additionalSmoothingRecordConfigOverride.enabled = additionalCorrelationOptionsModel.includes('insertAdditionalSmoothingRecord');
  state.isDisableContactFilter                          = additionalCorrelationOptionsModel.includes('disableContactFilter');
  state.isCreateUplinkCmdFile                          = additionalCorrelationOptionsModel.includes('createUplinkCommandFile');
  console.log("would submit")
  console.log(state.data)
}

defineExpose({ acceptSelectedTime });

const clockChangeRateModeChoices = ['COMPUTE_PREDICT', 'COMPUTE_INTERPOLATE', 'ASSIGN', 'NO_DRIFT']

const additionalCorrelationOptionItems = ref<CheckboxGroupItem[]>([
  {
    label: 'Insert additional smoothing record',
    description: 'Prepends an additional triplet ahead of the actual telemetry-based triplet to ensure SCLK-SCET continuity',
    value: 'insertAdditionalSmoothingRecord'
  },
  {
    label: 'Test Mode OWLT',
    description: 'Set a specific one-way light time for analysis or testing purposes, including when ephemerides are not available',
    value: 'testModeOwltEnabled'
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
  }
])

const additionalCorrelationOptionsModel = ref([])

// target-sample-exact-ert, target-sample-range-start-ert, target-sample-range-stop-ert, prior-correlation-exact-tdt
const timeSelectionMode = ref('none')

// composing / previewing
const timeCorrelationConfigState = ref('composing')

function cancelCorrelation() {
  emit('close-correlation');
}

async function previewCorrelation() {
  timeCorrelationConfigState.value = 'previewing'
  state['beginTime'] = toIso8601WithDiscardedTimezone(props.chartRange.start)
  state['endTime'] = toIso8601WithDiscardedTimezone(props.chartRange.end)
  const previewResults: TimeCorrelationPreviewResults = await runCorrelationPreview(state);
  delete state['beginTime']
  delete state['endTime']

  correlationPreviewResults.value = previewResults.correlationResults;
  emit('chart-show-preview-correlation-telemetry', previewResults);
}

function goBackToComposing() {
  timeCorrelationConfigState.value = 'composing'
  emit('chart-clear-preview-correlation-telemetry')
}

async function commitCorrelation() {
  const results: TimeCorrelationResults = await createCorrelation(state);
  emit('close-correlation');
  emit('chart-clear-preview-correlation-telemetry')
  emit('refresh-dashboard')
}

function toggleChoosingTargetSampleExactErt() {
  if (timeSelectionMode.value === 'none') {
    timeSelectionMode.value = 'target-sample-exact-ert';
    targetSampleExactErtPlaceholder.value = 'Click a point on the chart';
    emit('start-choosing-target-sample-ert');
  } else if (timeSelectionMode.value == 'target-sample-exact-ert') {
    timeSelectionMode.value = 'none';
    targetSampleExactErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
    emit('cancel-choosing');
  }
}

function toggleChoosingTargetSampleRangeStartErt() {
  if (timeSelectionMode.value === 'none') {
    timeSelectionMode.value = 'target-sample-range-start-ert';
    targetSampleRangeStartErtPlaceholder.value = 'Click a point on the chart';
    emit('start-choosing-target-sample-ert');
  } else if (timeSelectionMode.value == 'target-sample-range-start-ert') {
    timeSelectionMode.value = 'none';
    targetSampleRangeStartErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
    emit('cancel-choosing');
  }
}

function toggleChoosingTargetSampleRangeStopErt() {
  if (timeSelectionMode.value === 'none') {
    timeSelectionMode.value = 'target-sample-range-stop-ert';
    targetSampleRangeStopErtPlaceholder.value = 'Click a point on the chart';
    emit('start-choosing-target-sample-ert');
  } else if (timeSelectionMode.value == 'target-sample-range-stop-ert') {
    timeSelectionMode.value = 'none';
    targetSampleRangeStopErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
    emit('cancel-choosing');
  }
}

function toggleChoosingPriorCorrelationTdt() {
  if (timeSelectionMode.value === 'none') {
    timeSelectionMode.value = 'prior-correlation-exact-tdt'
    priorCorrelationTdtPlaceholder.value = 'Click a correlation on the chart';
    emit('start-choosing-prior-correlation-tdt');
  } else if (timeSelectionMode.value === 'prior-correlation-exact-tdt') {
    timeSelectionMode.value = 'none';
    priorCorrelationTdtPlaceholder.value = defaultPriorCorrelationTdtPlaceholder;
    emit('cancel-choosing');
  }
}

function acceptSelectedTime(timeStr) {
  switch(timeSelectionMode.value) {
    case 'target-sample-exact-ert':
      state.targetSampleExactErt = timeStr;
      break;
    case 'target-sample-range-start-ert':
      state.targetSampleRangeStartErt = timeStr;
      break;
    case 'target-sample-range-stop-ert':
      state.targetSampleRangeStopErt = timeStr;
      break;
    case 'prior-correlation-exact-tdt':
      state.priorCorrelationExactTdt = timeStr;
      break;
    default:
      console.warn('unexpected time str: ' + timeStr);
  }

  timeSelectionMode.value = 'none'
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

})

</script>

<template>
  <!-- @submit="onSubmit" -->
  <span v-if="timeCorrelationConfigState === 'composing'">
    <UForm :schema="schema" :state="state" class="space-y-4 pr-5 pt-5" style="min-height: 600px;">
      <UFormField label="Target sample (ERT)" name="targetSampleExactErt" size="sm" v-if="targetSampleSelectionType === 'exact'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
          <UInput v-model="state.targetSampleExactErt" :placeholder="targetSampleExactErtPlaceholder" class="w-full" :disabled="timeSelectionMode != 'none'"/>
          </div>
          <div class="col-span-1">
            <UButton @click="toggleChoosingTargetSampleExactErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionMode === 'target-sample-exact-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-exact-ert'].includes(timeSelectionMode))"/>
          </div>
        </div>
      </UFormField>

      <UFormField label="Target sample range begin (ERT)" name="targetSampleRangeStartErt" size="sm" v-if="targetSampleSelectionType === 'range'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.targetSampleRangeStartErt" :placeholder="targetSampleRangeStartErtPlaceholder" class="w-full" :disabled="timeSelectionMode != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingTargetSampleRangeStartErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionMode === 'target-sample-range-start-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-range-start-ert'].includes(timeSelectionMode))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <UFormField label="Target sample range end (ERT)" name="targetSampleRangeStopErt" size="sm" v-if="targetSampleSelectionType === 'range'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.targetSampleRangeStopErt" :placeholder="targetSampleRangeStopErtPlaceholder" class="w-full" :disabled="timeSelectionMode != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingTargetSampleRangeStopErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionMode === 'target-sample-range-stop-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-range-stop-ert'].includes(timeSelectionMode))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <UFormField label="Prior correlation for basis (TDT)" name="priorCorrelationExactTdt"  size="sm">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
            <UInput v-model="state.priorCorrelationExactTdt" :placeholder="priorCorrelationTdtPlaceholder" class="w-full"  :disabled="timeSelectionMode != 'none'"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingPriorCorrelationTdt" color="neutral" variant="outline" size="sm" :icon="timeSelectionMode === 'prior-correlation-exact-tdt' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'prior-correlation-exact-tdt'].includes(timeSelectionMode))"/>
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

  <span v-if="timeCorrelationConfigState === 'previewing'">
      <div class="pt-5" v-if="timeCorrelationConfigState === 'previewing'">
        <UButton @click="goBackToComposing" color="neutral" variant="outline">
          Back
        </UButton>

        <UButton @click="commitCorrelation" class="float-right">
          Commit
        </UButton>
      </div>
  </span>
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
