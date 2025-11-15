<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { Period, Range, Sale } from '~/types'
import { getPaginationRowModel } from '@tanstack/vue-table'

import type { OutputProductDef } from '@/services/mmtc-api.ts';
import { retrieveRunHistoryFileRows } from '@/services/mmtc-api';

const props = defineProps<{
  range: Range
}>()

const table = useTemplateRef('table')

const pagination = ref({
  pageIndex: 0,
  pageSize: 5
})

const runHistoryRows = ref([])

onMounted(async () => {
  runHistoryRows.value = await retrieveRunHistoryFileRows();
})

</script>

<template>
  <UTable
    ref="table"
    :data="runHistoryRows"
    v-model:pagination="pagination"
    class="shrink-0"
    sticky
    :ui="{
      base: 'table-fixed border-separate border-spacing-0',
      thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
      tbody: '[&>tr]:last:[&>td]:border-b-0',
      th: 'first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
      td: 'border-b border-default'
    }"
    :pagination-options="{
        getPaginationRowModel: getPaginationRowModel()
      }"
  />
  <div class="flex justify-center border-t border-default pt-4">
    <UPagination
      :default-page="(table?.tableApi?.getState().pagination.pageIndex || 0) + 1"
      :items-per-page="table?.tableApi?.getState().pagination.pageSize"
      :total="table?.tableApi?.getFilteredRowModel().rows.length"
      @update:page="(p) => table?.tableApi?.setPageIndex(p - 1)"
    />
  </div>
</template>
