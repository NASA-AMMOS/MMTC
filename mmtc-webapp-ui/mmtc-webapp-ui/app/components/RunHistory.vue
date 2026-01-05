<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn, } from '@nuxt/ui'
import type { Period, Range, Sale } from '~/types'
import { rollback } from '@/services/mmtc-api'
import { getPaginationRowModel } from '@tanstack/vue-table'
const UButton = resolveComponent('UButton')

import type { OutputProductDef } from '@/services/mmtc-api.ts';
import { retrieveRunHistoryFileRows } from '@/services/mmtc-api';

const props = defineProps<{

}>()

const emit = defineEmits([
  'refresh-dashboard'
])

defineExpose({ refresh });

const table = useTemplateRef('table')

const pagination = ref({
  pageIndex: 0,
  pageSize: 10
})

const runHistoryRows = ref([])
const currentRunId = ref('')

const cols = [
  {
    id: 'expand',
    cell: ({ row }) =>
      h(UButton, {
        color: 'neutral',
        variant: 'ghost',
        icon: 'i-lucide-chevron-down',
        square: true,
        'aria-label': 'Expand',
        ui: {
          leadingIcon: [
            'transition-transform',
            row.getIsExpanded() ? 'duration-200 rotate-180' : ''
          ]
        },
        onClick: () => row.toggleExpanded()
      })
  },
  {
    id: 'Run ID' ,
    header: 'Run ID',
    accessorKey: 'Run ID'
  },
  {
    id: 'Latest SCLK Kernel Post-run' ,
    header: 'SCLK Kernel Version',
    accessorKey: 'Latest SCLK Kernel Post-run'
  },
  {
    id: 'Run Time' ,
    header: 'Created on',
    accessorKey: 'Run Time'
  },
  { id: 'action' },
]

const rollbackInProgress = ref(false);
async function performRollback(runId, modalCloseFunc) {
  rollbackInProgress.value = true
  if (runId === 'Initial State') {
    runId = '0' // this fits the actual MMTC API
  }
  const rollbackResult = await rollback(runId);
  rollbackInProgress.value = false;
  modalCloseFunc();
  emit('refresh-dashboard')
}

onMounted(async () => {
  refresh();
})

async function refresh() {
  const actualRunHistoryRows = await retrieveRunHistoryFileRows();

  if (actualRunHistoryRows.length > 0) {
    currentRunId.value = actualRunHistoryRows[0]['Run ID'];
  } else {
    currentRunId.value = 'Initial State';
  }

  const initialRunHistoryRow = {
      'Run ID': 'Initial State',
      'Latest SCLK Kernel Post-run': 'N/A',
      'Run Time': 'N/A'
    }
  runHistoryRows.value = actualRunHistoryRows.concat([initialRunHistoryRow]);
}

</script>

<template>
  <UTable
    ref="table"
    :data="runHistoryRows"
    :columns="cols"
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
    data-testid="run-history-table"
  >
    <template #expanded="{ row }">
        <pre class="max-w-full whitespace-pre-wrap break-words">{{ row.original }}</pre>
    </template>
    <template #action-cell="{ row }">
        <UModal :dismissable="!rollbackInProgress">

          <UTooltip text="Roll back to this state">
            <UButton size="xs" icon="i-lucide-arrow-big-left-dash" v-if="row.original['Run ID'] != currentRunId" :data-testid="`invoke-rollback-button-${row.original['Run ID']}`"/>
          </UTooltip>
          <!-- @click="confirmRollback(row.original)" -->
          <template #header>
            <strong>Output product rollback</strong>
          </template>
          <template #body>
              <p>
              This operation allows users to revert all of MMTC's output products to their state as they were after a prior time correlation run.<br/><br/>
                You have selected to revert all products to their version as of <strong>{{ row.original['Run ID'] === 'Initial State' ? 'their initial state' : `the completion of run ${row.original['Run ID']}` }}</strong>.<br/><br/>

              If you proceed, the following will occur:<br/>
                1. A backup of the current state of all output products will be archived into a zip file at a preconfigured location<br/>
                2. Output products will be removed or truncated as appropriate to restore their prior state<br/>
                <br/>

                This operation is non-reversible.
                <br/><br/>
              </p>
          </template>
          <template #footer="{close}">
            <div class="w-full">
            <UButton
              color="neutral"
              variant="outline"
              class="float-left"
              :disabled="rollbackInProgress"
              @click="close"
            >
              Cancel
            </UButton>
            <UButton
              color="warning"
              class="float-right"
              :loading="rollbackInProgress"
              :disabled="rollbackInProgress"
              @click="performRollback(row.original['Run ID'], close);"
              data-testid="confirm-rollback-button"
            >
              Proceed
            </UButton>
            </div>
          </template>

        </UModal>
    </template>
  </UTable>
  <div class="flex justify-center border-t border-default pt-4">
    <UPagination
      :default-page="(table?.tableApi?.getState().pagination.pageIndex || 0) + 1"
      :items-per-page="table?.tableApi?.getState().pagination.pageSize"
      :total="table?.tableApi?.getFilteredRowModel().rows.length"
      @update:page="(p) => table?.tableApi?.setPageIndex(p - 1)"
    />
  </div>
</template>
