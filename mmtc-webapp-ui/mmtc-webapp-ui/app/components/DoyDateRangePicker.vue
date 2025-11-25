<script setup lang="ts">

// from https://ui.nuxt.com/docs/components/calendar#as-a-daterangepicker

import { DateFormatter, getLocalTimeZone, CalendarDate, today } from '@internationalized/date'
import {toDoy, UnifiedCalendarDateRange, toYyyyDd, calendarDateToDoy} from '../services/utils'
import { parseISO, isBefore } from 'date-fns'

const range = ref<UnifiedCalendarDateRange>(new UnifiedCalendarDateRange());

defineExpose({ setInitialCalendarDates });

const props = defineProps<{
  disabled: boolean
}>()

function setInitialCalendarDates(beginCalendarDate, endCalendarDate) {
  console.log("setting initial calendar dates:")
  console.log(beginCalendarDate)
  console.log(endCalendarDate)

  range.value.updateBeginWithCalendarDate(beginCalendarDate);
  range.value.updateEndWithCalendarDate(endCalendarDate);

  modelValue.value = {
    start: beginCalendarDate,
    end: endCalendarDate
  }
}

const emit = defineEmits(['update-date-range'])

watch(
  range,
  (newVal, oldVal) => {
    if (isBefore(range.value.beginDate, range.value.endDate)) {
      console.log('emitting update-date-range')
      emit('update-date-range', range.value.getCopy());
    } else {
      console.log('full range not yet set')
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
    if (modelValue.value.start != undefined) {
      range.value.updateBeginWithCalendarDate(modelValue.value.start);
    }
    if (modelValue.value.end != undefined) {
      range.value.updateEndWithCalendarDate(modelValue.value.end);
    }
  },
  { deep: true }
)

</script>
<template>
  <!--{{ range.beginYear }} / {{ range.beginDoy }} - {{ range.endYear }} / {{ range.endDoy }}-->
  <UPopover>
    <UButton color="neutral" variant="subtle" icon="i-lucide-calendar">
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
            <div class="text-xs">
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
    </template>
  </UPopover>
</template>
