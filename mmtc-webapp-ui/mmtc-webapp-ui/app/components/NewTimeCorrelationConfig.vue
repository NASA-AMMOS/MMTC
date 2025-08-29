<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { DefaultTimeCorrelationConfig, AdditionalSmoothingRecordConfig } from '@/services/mmtc-api';
import { getDefaultCorrelationConfig, runCorrelationPreview, createCorrelation } from '@/services/mmtc-api';
import {toUtcIso8601WithDiscardedTimezone, UnifiedCalendarDateRange} from "../services/utils";
import {TimeCorrelationPreviewResults} from "../services/mmtc-api";
import TimeCorrTargetDisplay from "./TimeCorrTargetDisplay.vue";

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

const doyIsoFormat = "YYYY-DDDTHH:MM:SS[.ssssss]";

function isDoyIsoFormatDatetime(s: string) {
  if (s === undefined) {
    return false;
  }

  // todo complete me
  if (s.length < 17) {
    return false;
  }

  return true;
}

const form = useTemplateRef('form')

const schema = z.object({
  targetSampleInputErtMode: z.string(),
  targetSampleRangeStartErt: z.string().optional(),  // the ERT inputs are handled in a superRefine below
  targetSampleRangeStopErt: z.string().optional(),
  targetSampleExactErt: z.string().optional(),
  priorCorrelationExactTdt: z.string().optional(),
  testModeOwltEnabled: z.boolean(),
  testModeOwltSec: z.number().optional(),     // handled in a superRefine below
  clockChangeRateConfig: z.object({
    clockChangeRateModeOverride: z.string(),
    specifiedClockChangeRateToAssign: z.any() // handled in a superRefine below
  }),
  additionalSmoothingRecordConfigOverride: z.object({
      enabled: z.boolean(),
      coarseSclkTickDuration: z.any()        // handled in a superRefine below
  }),
  isDisableContactFilter: z.boolean(),
  isCreateUplinkCmdFile: z.boolean(),
  dryRunConfig: z.object({
    mode: z.string(),
    sclkKernelOutputPath: z.string().optional()
  })
}).superRefine((obj, ctx) => {
  if (!(isDoyIsoFormatDatetime(obj.targetSampleExactErt))) {
    if (isDoyIsoFormatDatetime(obj.targetSampleRangeStartErt) && isDoyIsoFormatDatetime(obj.targetSampleRangeStopErt)) {
      return;
    }

    ctx.addIssue({
      code: "custom",
      origin: "string",
      message: `Must supply an ERT matching ${doyIsoFormat}`,
      input: obj,
      path: ["targetSampleExactErt"]
    });

    if (!isDoyIsoFormatDatetime(obj.targetSampleRangeStartErt)) {
      ctx.addIssue({
        code: "custom",
        origin: "string",
        message: `Must supply an ERT matching ${doyIsoFormat}`,
        input: obj,
        path: ["targetSampleRangeStartErt"]
      });
    }

    if (!isDoyIsoFormatDatetime(obj.targetSampleRangeStopErt)) {
      ctx.addIssue({
        code: "custom",
        origin: "string",
        message: `Must supply an ERT matching ${doyIsoFormat}`,
        input: obj,
        path: ["targetSampleRangeStopErt"]
      });
    }
  }
})
.superRefine((obj, ctx) => {
  if (isDoyIsoFormatDatetime(obj.targetSampleRangeStartErt)) {
    if (isDoyIsoFormatDatetime(obj.targetSampleRangeStopErt)) {
      if (! (obj.targetSampleRangeStartErt < obj.targetSampleRangeStopErt)) {
        ctx.addIssue({
          code: "custom",
          origin: "string",
          message: "The end of the ERT range must be after the start",
          input: obj,
          path: ["targetSampleRangeStopErt"]
        })
      }
    }
  }
})
.superRefine((obj, ctx) => {
  if (obj.clockChangeRateConfig.clockChangeRateModeOverride === 'ASSIGN') {
    const res = z.number().safeParse(obj.clockChangeRateConfig.specifiedClockChangeRateToAssign);
    if (! res.success) {
      for (const issue of res.error.issues) {
        ctx.addIssue({
          ...issue,
          path: ["clockChangeRateConfig", "specifiedClockChangeRateToAssign"]
        });
      }
    }
  }
})
.superRefine((obj, ctx) => {
  if (obj.testModeOwltEnabled) {
    const res = z.number().safeParse(obj.testModeOwltSec);
    if (! res.success) {
      for (const issue of res.error.issues) {
        ctx.addIssue({
          ...issue,
          path: ["testModeOwltSec"]
        });
      }
    }
  }
})
.superRefine((obj, ctx) => {
    if (obj.additionalSmoothingRecordConfigOverride.enabled) {
      let res = z.int().min(1).safeParse(obj.additionalSmoothingRecordConfigOverride.coarseSclkTickDuration);
      if (! res.success) {
        for (const issue of res.error.issues) {
          ctx.addIssue({
            ...issue,
            path: ["additionalSmoothingRecordConfigOverride", "coarseSclkTickDuration"]
          });
        }
      }

      if (obj.clockChangeRateConfig.clockChangeRateModeOverride === 'COMPUTE_INTERPOLATE') {
        ctx.addIssue({
          code: "custom",
          origin: "string",
          message: "Cannot use COMPUTE_INTERPOLATE with additional smoothing record",
          input: obj,
          path: ["clockChangeRateConfig", "clockChangeRateModeOverride"]
        });
      }
    }
  });

type Schema = z.output<typeof schema>

const newCorrelationMinTdt = ref(0.0);
const newCorrelationMinLookbackHours = ref(0.0);
const newCorrelationMaxLookbackHours = ref(0.0);

const targetSampleExactTdt = ref(0.0);
const targetSampleRangeStartTdt = ref(0.0);
const targetSampleRangeStopTdt = ref(Infinity);

const priorLookbackMinTdt = ref(0.0);
const priorLookbackMaxTdt = ref(Infinity);

const state = reactive<Partial<Schema>>({
  targetSampleInputErtMode: 'RANGE',
  targetSampleRangeStartErt: undefined,
  targetSampleRangeStopErt: undefined,
  targetSampleExactErt: undefined,
  priorCorrelationExactTdt: undefined,
  testModeOwltEnabled: false,
  testModeOwltSec: 0.0,
  clockChangeRateConfig: {
    clockChangeRateModeOverride: "COMPUTE_INTERPOLATE",
    specifiedClockChangeRateToAssign: 1.0
  },
  additionalSmoothingRecordConfigOverride: {
    enabled: false,
    coarseSclkTickDuration: 100
  },
  isDisableContactFilter: false,
  isCreateUplinkCmdFile: false,
  dryRunConfig: {
    mode: 'NOT_DRY_RUN',
    sclkKernelOutputPath: ""
  }
})

const stateValidation = computed(() => schema.safeParse(state));
const stateIsValid = computed(() => stateValidation.value.success);
const fieldErrors = computed(() => stateValidation.value.success ? {} : stateValidation.value.error.flatten().fieldErrors);

const previewOrCommitIsRunning = ref(false);

const ertInputsAreComplete: boolean = computed(() => {
  if (isDoyIsoFormatDatetime(state.targetSampleExactErt)) {
    return true;
  }

  return isDoyIsoFormatDatetime(state.targetSampleRangeStartErt) && isDoyIsoFormatDatetime(state.targetSampleRangeStopErt);
});

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

const toast = useToast()

defineExpose({ acceptSelectedTime });

const clockChangeRateModeChoices = ['COMPUTE_PREDICT', 'COMPUTE_INTERPOLATE', 'ASSIGN', 'NO_DRIFT'];
const targetSampleInputErtModeChoices = ref(['RANGE', 'EXACT']);

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
  previewOrCommitIsRunning.value = true;
  state['beginTimeErt'] = toUtcIso8601WithDiscardedTimezone(props.range.beginDate)
  state['endTimeErt'] = toUtcIso8601WithDiscardedTimezone(props.range.endDate)
  const previewResults: TimeCorrelationPreviewResults = await runCorrelationPreview(state);
  delete state['beginTimeErt']
  delete state['endTimeErt']

  correlationPreviewResults.value = previewResults.correlationResults;
  emit('chart-show-correlation-preview', previewResults);
  timeCorrelationConfigState.value = 'previewing'
  previewOrCommitIsRunning.value = false;
}

function goBackToComposing() {
  timeCorrelationConfigState.value = 'composing'
  emit('chart-clear-correlation-preview')
}

watch(additionalCorrelationOptionsModel, () => {
  state.testModeOwltEnabled                             = additionalCorrelationOptionsModel.value.includes('testModeOwltEnabled');
  state.additionalSmoothingRecordConfigOverride.enabled = additionalCorrelationOptionsModel.value.includes('insertAdditionalSmoothingRecord');
  state.isDisableContactFilter                          = additionalCorrelationOptionsModel.value.includes('disableContactFilter');
  state.isCreateUplinkCmdFile                           = additionalCorrelationOptionsModel.value.includes('createUplinkCommandFile');

  // manually call form validation to revalidate form after selection has occurred
  form.value.validate({silent: true});
})

async function commitCorrelation() {
  previewOrCommitIsRunning.value = true;
  const results: TimeCorrelationResults = await createCorrelation(state);
  emit('close-correlation');
  emit('chart-clear-correlation-preview')
  emit('refresh-dashboard')
  previewOrCommitIsRunning.value = false;
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
      targetSampleExactErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
      break;
    case 'target-sample-range-start-ert':
      state.targetSampleRangeStartErt = selection.ertStr;
      targetSampleRangeStartTdt.value = selection.tdt;
      targetSampleRangeStartErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
      break;
    case 'target-sample-range-stop-ert':
      state.targetSampleRangeStopErt = selection.ertStr;
      targetSampleRangeStopTdt.value = selection.tdt;
      targetSampleRangeStopErtPlaceholder.value = defaultTargetSampleErtPlaceholder;
      break;
    case 'prior-correlation-exact-tdt':
      state.priorCorrelationExactTdt = selection.tdt;
      priorCorrelationTdtPlaceholder.value = defaultPriorCorrelationTdtPlaceholder;
      break;
    default:
      console.warn('unexpected time str: ' + selection);
  }

  resetTimeSelectionCfg();

  // manually call form validation to revalidate form after selection has occurred
  form.value.validate({silent: true});
}

onMounted(async () => {
  defaultCorrelationConfig.value = await getDefaultCorrelationConfig();

  // only allow exact target sample selection when samples per set is 1
  if (defaultCorrelationConfig.value.samplesPerSet === 1) {
    state.targetSampleInputErtMode = 'EXACT'
    targetSampleInputErtModeChoices.value = ['EXACT', 'RANGE']
  } else {
    state.targetSampleInputErtMode = 'RANGE'
    targetSampleInputErtModeChoices.value = ['RANGE']
  }

  state.clockChangeRateConfig.clockChangeRateModeOverride = defaultCorrelationConfig.value.clockChangeRateConfig.clockChangeRateModeOverride;
  state.additionalSmoothingRecordConfigOverride = defaultCorrelationConfig.value.additionalSmoothingRecordConfigOverride;

  newCorrelationMinTdt.value = defaultCorrelationConfig.value.newCorrelationMinTdt;
  newCorrelationMinLookbackHours.value = defaultCorrelationConfig.value.predictedClkRateMinLookbackHours;
  newCorrelationMaxLookbackHours.value = defaultCorrelationConfig.value.predictedClkRateMaxLookbackHours;
})

</script>

<template>
  <!-- @submit="onSubmit" -->
  <div v-if="timeCorrelationConfigState === 'composing'"  class="pr-5 pt-5">
    <p class="text-xl font-bold text-center text-primary">New Correlation Configuration</p>
    <UForm ref="form" :schema="schema" :state="state" :validate-on="['input','change','blur']" class="space-y-4 pt-5" style="min-height: 600px;">
      <UFormField v-if="targetSampleInputErtModeChoices.length > 1" label="Target sample selection type" name="targetSampleInputErtMode" size="sm">
        <USelect v-model="state.targetSampleInputErtMode" :items="targetSampleInputErtModeChoices" class="w-full"/>
      </UFormField>

      <UFormField label="Target sample (ERT)" name="targetSampleExactErt" size="sm" v-if="state.targetSampleInputErtMode === 'EXACT'">
        <div class="grid grid-cols-4 gap-x-2">
          <div class="col-span-3">
          <UInput v-model="state.targetSampleExactErt" :placeholder="targetSampleExactErtPlaceholder" class="w-full" :disabled="timeSelectionCfg.selectionDestination != 'none'"/>
          </div>
          <div class="col-span-1">
            <UButton @click="toggleChoosingTargetSampleExactErt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'target-sample-exact-ert' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample-exact-ert'].includes(timeSelectionCfg.selectionDestination))"/>
          </div>
        </div>
      </UFormField>

      <UFormField label="Target sample range begin (ERT)" name="targetSampleRangeStartErt" size="sm" v-if="state.targetSampleInputErtMode === 'RANGE'">
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

      <UFormField label="Target sample range end (ERT)" name="targetSampleRangeStopErt" size="sm" v-if="state.targetSampleInputErtMode === 'RANGE'">
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
            <UInput v-model="state.priorCorrelationExactTdt" :placeholder="priorCorrelationTdtPlaceholder" class="w-full" :disabled="(!ertInputsAreComplete) || (timeSelectionCfg.selectionDestination != 'none')"/>
          </div>
          <div class="col-span-1">
            <UTooltip text="Click to select a point from the chart">
              <UButton @click="toggleChoosingPriorCorrelationTdt" color="neutral" variant="outline" size="sm" :icon="timeSelectionCfg.selectionDestination === 'prior-correlation-exact-tdt' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="(!ertInputsAreComplete) || (! (['none', 'prior-correlation-exact-tdt'].includes(timeSelectionCfg.selectionDestination)))"/>
            </UTooltip>
          </div>
        </div>
      </UFormField>

      <div class="flex gap-4 items-end">
        <UFormField label="Clock change rate mode" name="clockChangeRateConfig.clockChangeRateModeOverride" size="sm">
          <USelect v-model="state.clockChangeRateConfig.clockChangeRateModeOverride" :items="clockChangeRateModeChoices" class="w-full"/>
        </UFormField>

        <UFormField label="Clock rate" style="max-width: 100px;" name="clockChangeRateConfig.specifiedClockChangeRateToAssign" v-if="state.clockChangeRateConfig.clockChangeRateModeOverride === 'ASSIGN'" size="sm">
          <UInput v-model="state.clockChangeRateConfig.specifiedClockChangeRateToAssign" type="number" class="w-full" placeholder="Rate"/>
        </UFormField>
      </div>

      <UCheckboxGroup v-model="additionalCorrelationOptionsModel" :items="additionalCorrelationOptionItems" size="sm" />

      <UFormField label="Smoothing record duration (SCLK ticks)" name="additionalSmoothingRecordConfigOverride.coarseSclkTickDuration" :style="{visibility: additionalCorrelationOptionsModel.includes('insertAdditionalSmoothingRecord') ? 'visible' : 'hidden'}" size="sm">
        <UInput v-model="state.additionalSmoothingRecordConfigOverride.coarseSclkTickDuration" type="number" class="w-full"/>
      </UFormField>

      <UFormField label="Test OWLT (sec)" name="testModeOwltSec" :style="{visibility: additionalCorrelationOptionsModel.includes('testModeOwltEnabled') ? 'visible' : 'hidden'}" size="sm">
        <UInput v-model="state.testModeOwltSec" type="number" class="w-full"/>
      </UFormField>

      <div class="pt-5">
        <UTooltip text="Cancel correlation configuration">
          <UButton @click="cancelCorrelation" color="neutral" variant="outline" :disabled="previewOrCommitIsRunning">
            Cancel
          </UButton>
        </UTooltip>

        <UTooltip text="View the results of the new correlation run without committing the changes to output products">
          <UButton @click="previewCorrelation" :disabled="!stateIsValid" class="float-right" loading-auto>
            Preview
          </UButton>
        </UTooltip>
      </div>
      <!--div>
        {{fieldErrors}}
      </div-->
    </UForm>
  </div>

  <div v-if="timeCorrelationConfigState === 'previewing'" class="pr-5 pt-5">
    <div>
      <p class="text-xl font-bold text-center text-primary">New Correlation Preview</p>
      <div class="mt-5">
        <UIcon name="i-lucide-target" class="size-3"/>
        <span class="text-sm font-bold">
          Target sample
        </span>
        <TimeCorrTargetDisplay :correlation="correlationPreviewResults.correlation"/>
      </div>
      <USeparator class="mt-8 mb-8"/>
      <div class="mt-2 mb-2">
        <UIcon name="i-lucide-list" class="size-3"/>
        <span class="text-sm font-bold">
          New correlation records
        </span>
        <div class="mb-5"/>
        <div v-if="correlationPreviewResults.correlation.updatedInterpolatedTriplet">
          <TripletDisplay title="Updated existing triplet" :triplet="correlationPreviewResults.correlation.updatedInterpolatedTriplet"/>
        </div>
        <div v-if="correlationPreviewResults.correlation.newSmoothingTriplet">
          <TripletDisplay title="New smoothing triplet" :triplet="correlationPreviewResults.correlation.newSmoothingTriplet"/>
        </div>
        <div v-if="correlationPreviewResults.correlation.newPredictedTriplet">
          <TripletDisplay title="New predictive triplet" :triplet="correlationPreviewResults.correlation.newPredictedTriplet"/>
        </div>
      </div>
    </div>
      <div class="pt-5">
        <UTooltip text="Return to correlation configuration">
          <UButton @click="goBackToComposing" color="neutral" variant="outline" :disabled="previewOrCommitIsRunning">
            Back
          </UButton>
        </UTooltip>

        <UTooltip text="Commit the correlation, producing all related output products">
          <UButton @click="commitCorrelation" class="float-right" loading-auto>
            Commit
          </UButton>
        </UTooltip>
      </div>
  </div>
</template>
