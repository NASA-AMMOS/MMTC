<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'

const emit = defineEmits([
  'cancel-correlation',
  'start-choosing-target-sample-ert',
  'start-choosing-prior-correlation-ert',
  'cancel-choosing-ert'
])

// todo update schema
const schema = z.object({
  email: z.email('Invalid email'),
  password: z.string('Password is required').min(8, 'Must be at least 8 characters')
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  targetSampleErt: undefined,
  targetSampleErtRangeStart: undefined,
  targetSampleErtRangeEnd: undefined,
  priorCorrelationErt: undefined,
  clockChangeRateMode: undefined,
  specifiedClockChangeRateToAssign: undefined,
  testModeOwltSec: undefined,
})

const defaultErtPlaceholder = 'YYYY-DDDTHH:MM:SS.ssssss'
const targetSampleErtPlaceholder = ref(defaultErtPlaceholder)
const priorCorrelationErtPlaceholder = ref(defaultErtPlaceholder)

const toast = useToast()
async function onSubmit(event: FormSubmitEvent<Schema>) {
  toast.add({ title: 'Success', description: 'The form has been submitted.', color: 'success' })
  console.log(event.data)
}

defineExpose({ acceptSelectedErt });

const clockChangeRateModeChoices = ['predict-compute', 'predict-interpolate', 'assign', 'nodrift']

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

const ertSelectionMode = ref('none')

function cancelCorrelation() {
  emit('cancel-correlation');
}

function startChoosingTargetSampleErt() {
  if (ertSelectionMode.value === 'none') {
    ertSelectionMode.value = 'target-sample';
    targetSampleErtPlaceholder.value = 'Click a point on the chart';
    emit('start-choosing-target-sample-ert');
  } else if (ertSelectionMode.value == 'target-sample') {
    ertSelectionMode.value = 'none';
    targetSampleErtPlaceholder.value = defaultErtPlaceholder;
    emit('cancel-choosing-ert');
  }
}

function startChoosingPriorCorrelationErt() {
  if (ertSelectionMode.value === 'none') {
    ertSelectionMode.value = 'prior-correlation'
    priorCorrelationErtPlaceholder.value = 'Click a correlation on the chart';
    emit('start-choosing-prior-correlation-ert');
  } else if (ertSelectionMode.value === 'prior-correlation') {
    ertSelectionMode.value = 'none';
    priorCorrelationErtPlaceholder.value = defaultErtPlaceholder;
    emit('cancel-choosing-ert');
  }
}

function acceptSelectedErt(ertStr) {
  if (ertSelectionMode.value === 'target-sample') {
    state.targetSampleErt = ertStr;
    targetSampleErtPlaceholder.value = defaultErtPlaceholder;
    ertSelectionMode.value = 'none';
  } else if (ertSelectionMode.value === 'prior-correlation') {
    state.priorCorrelationErt = ertStr;
    priorCorrelationErtPlaceholder.value = defaultErtPlaceholder;
    ertSelectionMode.value = 'none';
  } else {
    console.warn('unexpected ert str: ' + ertStr);
  }
}

function cancelErtSelection() {
  emit('cancel-choosing-ert');
}

watch(additionalCorrelationOptionsModel, (newVal, oldVal) => {
  // console.log(additionalCorrelationOptionsModel.value);
})

</script>

<template>
  <UForm :schema="schema" :state="state" class="space-y-4 pr-5 pt-5" @submit="onSubmit" style="min-height: 600px;">
    <UFormField label="Target sample (ERT)" name="targetSampleErt" size="sm">
      <div class="grid grid-cols-4 gap-x-2">
        <div class="col-span-3">
        <UInput v-model="state.targetSampleErt" :placeholder="targetSampleErtPlaceholder" class="w-full" :disabled="ertSelectionMode != 'none'"/>
        </div>
        <div class="col-span-1">
          <UButton @click="startChoosingTargetSampleErt" color="neutral" variant="outline" size="sm" :icon="ertSelectionMode === 'target-sample' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'target-sample'].includes(ertSelectionMode))"/>
        </div>
      </div>

    </UFormField>

    <UFormField label="Prior correlation for basis (ERT)" name="priorCorrelationErt"  size="sm">
      <div class="grid grid-cols-4 gap-x-2">
        <div class="col-span-3">
          <UInput v-model="state.priorCorrelationErt" :placeholder="priorCorrelationErtPlaceholder" class="w-full"  :disabled="ertSelectionMode != 'none'"/>
        </div>
        <div class="col-span-1">
          <UButton @click="startChoosingPriorCorrelationErt" color="neutral" variant="outline" size="sm" :icon="ertSelectionMode === 'prior-correlation' ? 'i-lucide-mouse-pointer-2-off' : 'i-lucide-mouse-pointer-click'" :disabled="! (['none', 'prior-correlation'].includes(ertSelectionMode))"/>
        </div>
      </div>
    </UFormField>

    <UFormField label="Clock change rate mode" name="clockChangeRateMode" size="sm">
      <div class="grid grid-cols-4 gap-x-2">
        <div class="col-span-3">
          <USelect v-model="state.clockChangeRateMode" :items="clockChangeRateModeChoices" class="w-full"/>
        </div>
        <div class="col-span-1" v-if="state.clockChangeRateMode === 'assign'">
          <UInput v-model="state.specifiedClockChangeRateToAssign" class="w-full"/>
        </div>
      </div>
    </UFormField>


    <UCheckboxGroup v-model="additionalCorrelationOptionsModel" :items="additionalCorrelationOptionItems" size="sm" />

    <!-- v-show="additionalCorrelationOptionsModel.includes('testModeOwltEnabled')"  -->
    <UFormField label="Test Mode OWLT (sec)" name="testModeOwltSec" :style="{visibility: additionalCorrelationOptionsModel.includes('testModeOwltEnabled') ? 'visible' : 'hidden'}" size="sm">
      <UInput v-model="state.testModeOwltSec" class="w-full"/>
    </UFormField>

    <div class="pt-5">
    <UButton @click="cancelCorrelation" color="neutral" variant="outline">
      Cancel
    </UButton>

    <UButton type="submit" class="float-right">
      Submit
    </UButton>
    </div>
  </UForm>
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
