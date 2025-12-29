<script setup lang="ts">

// from https://ui.nuxt.com/docs/components/calendar#as-a-daterangepicker

import { DateFormatter, getLocalTimeZone, CalendarDate, today } from '@internationalized/date'
import {toDoy, UnifiedCalendarDateRange, toYyyyDd, calendarDateToDoy} from '../services/utils'
import { parseISO, isBefore } from 'date-fns'

const range = ref<UnifiedCalendarDateRange>(new UnifiedCalendarDateRange());
const rangeStartIsUndefined = ref(false);
const rangeEndIsUndefined = ref(false);

defineExpose({ setInitialCalendarDates, setQuickSelectOptions });

const props = defineProps<{
  disabled: boolean
}>()

function setInitialCalendarDates(beginCalendarDate, endCalendarDate) {
  range.value.updateBeginWithCalendarDate(beginCalendarDate);
  range.value.updateEndWithCalendarDate(endCalendarDate);

  modelValue.value = {
    start: beginCalendarDate,
    end: endCalendarDate
  }
}

const tenQuickSelectOptions = ref([{},{},{},{},{},{},{},{},{},{}]);

function setQuickSelectOptions(newTenQuickSelectOptions) {
  tenQuickSelectOptions.value = newTenQuickSelectOptions;
}

const emit = defineEmits(['update-date-range'])

function quickSelect(quickSelectOption) {
  setInitialCalendarDates(quickSelectOption.startCalDate, quickSelectOption.endCalDate);
  return true;
}

watch(
  range,
  (newVal, oldVal) => {
    if (rangeStartIsUndefined.value == false && rangeEndIsUndefined.value == false) {
      if (isBefore(range.value.beginDate, range.value.endDate)) {
        emit('update-date-range', range.value.getCopy());
      }
    }
  },
  { deep: true }
)

const modelValue = shallowRef({
  start: today('UTC'),
  end: today('UTC')
})

watch(
  () => modelValue,
  (newVal, oldVal) => {
    if (modelValue.value.start == undefined) {
      rangeStartIsUndefined.value = true;
    } else {
      rangeStartIsUndefined.value = false;
      range.value.updateBeginWithCalendarDate(modelValue.value.start);
    }

    if (modelValue.value.end == undefined) {
      rangeEndIsUndefined.value = true;
    } else {
      rangeEndIsUndefined.value = false;
      range.value.updateEndWithCalendarDate(modelValue.value.end);
    }
  },
  { deep: true }
)

</script>
<template>
  <span :class="{ 'doyDatePickerDisabled': disabled }">
    <UPopover :class="{ 'doyDatePickerDisablePointerEventsNone': disabled }">
      <UButton color="neutral" variant="outline" icon="i-lucide-calendar">
        <template v-if="modelValue.start">
          <template v-if="modelValue.end">
            {{ range.beginYear }}-{{ range.beginDoy }} to {{ range.endYear }}-{{ range.endDoy }}
          </template>

          <template v-else>
            {{ range.beginYear }}-{{ range.beginDoy }}
          </template>
        </template>
        <template v-else>
          Pick a date range
        </template>
      </UButton>

      <template #content>
        <div class="grid grid-cols-5 gap-3 ml-3 mr-3 pt-2">
          <div class="col-span-5">
            Quick select
          </div>


          <div class="col-span-1" v-for="quickSelectOption in tenQuickSelectOptions">
            <UButton @click="quickSelect(quickSelectOption)" :disabled="props.disabled" color="primary" variant="subtle" size="sm" class="text-[var(--ui-fg)]" style="width: 120px;">
              {{ quickSelectOption.displayText }}
            </UButton>
          </div>
          <div class="col-span-5">
          <USeparator/>
          </div>
          <div class="col-span-5">
            Calendar select
          </div>
        </div>
        <div class="grid grid-cols-5 gap-3">
          <div class="col-span-5">
            <UCalendar
              v-model="modelValue"
              class="p-2"
              :number-of-months="2"
              range
              :disabled="props.disabled"
              :readonly="props.disabled"
              :disable-days-outside-current-view="true"
            >
              <template #day="{ day }">
                <!-- Don't render anything for days outside the current month,
                     but keep the grid cell so alignment stays -->
                <div>
                  <div class="text-xs" style="font-size: 11px">
                    <strong>
                      {{ calendarDateToDoy(day) }}
                    </strong>
                  </div>
                  <div style="font-size: 9px;">
                    {{ day.day }}
                  </div>
                </div>


                <!-- For outside days, render an empty placeholder of same size -->

              </template>
            </UCalendar>
          </div>
        </div>
      </template>
    </UPopover>
  </span>
</template>

<style scoped>
  .doyDatePickerDisabled {
    opacity: 0.75;
    cursor: not-allowed;
  }
  .doyDatePickerDisablePointerEventsNone {
    pointer-events: none;
  }
</style>
