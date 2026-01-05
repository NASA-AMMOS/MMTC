<script setup lang="ts">
// import type { ConfigFile } from '@/services/mmtc-api.ts';
import type { OutputProductDef } from '@/services/mmtc-api.ts';
import { retrieveOutputProductDefs, retrieveOutputProductFileContents, retrieveOutputProductFileContentsAsTable } from '@/services/mmtc-api';

const props = defineProps(['outputProductDefName'])

const allOutputProductDefs = ref([])
const outputProductDef = ref({
  name: '',
  builtIn: false,
  simpleClassName: '',
  type: '',
  singleFile: false,
  filenames: []
})
const outputProductDefDisplayName = ref('')

const fileSelectionOptions = ref([])
const fileSelectionChoice = ref('')

const viewMode = ref('plaintext') // 'plaintext' or 'table'
const fileContentsToDisplay = ref('No file selected.');
const fileContentTableColumnsToDisplay = ref([]);
const fileContentTableRowsToDisplay = ref([]);

watch([outputProductDef, fileSelectionChoice], async (newFileSelectionChoice, oldFileSelectionChoice) => {
  updateFileContents();
})

async function updateFileContents() {
  if (fileSelectionChoice.value !== '') {
    if (fileSelectionChoice.value.endsWith('.csv')) {
      const fileContentsAsTable = await retrieveOutputProductFileContentsAsTable(props.outputProductDefName, fileSelectionChoice.value)
      viewMode.value = 'table';

      const cols = [];
      fileContentsAsTable.columns.forEach(colName => {
        cols.push({
          accessorKey: colName,
          header: colName
        })
      });

      fileContentTableColumnsToDisplay.value = cols;
      fileContentTableRowsToDisplay.value = fileContentsAsTable.rows;
    } else {
      const fileContents = await retrieveOutputProductFileContents(props.outputProductDefName, fileSelectionChoice.value)
      viewMode.value = 'plaintext';
      fileContentsToDisplay.value = fileContents;
    }
  } else {
    viewMode.value = 'plaintext';
    fileContentsToDisplay.value = 'No file selected.';
  }
}

async function updateSelectedProductDef() {
  allOutputProductDefs.value.forEach((def) => {
    if (def.name === props.outputProductDefName) {
      outputProductDef.value = def
    }
  })

  outputProductDefDisplayName.value = outputProductDef.value.displayName;

  fileSelectionOptions.value = outputProductDef.value.filenames;
  if (outputProductDef.value.filenames.length > 0) {
    fileSelectionChoice.value = fileSelectionOptions.value[0];
  } else {
    fileSelectionChoice.value = ''
  }
}

watch(() => props.outputProductDefName, async (newDef, oldDef) => {
  updateSelectedProductDef();
})

onMounted(async () => {
  allOutputProductDefs.value = await retrieveOutputProductDefs();
  updateSelectedProductDef();
})

async function downloadFile() {
  const fileContents = await retrieveOutputProductFileContents(props.outputProductDefName, fileSelectionChoice.value)
  const fileBlob = new Blob([fileContents], {type : 'text/plan'});
  const fileUrl = URL.createObjectURL(fileBlob);

  const tmpLink = document.createElement('a');
  tmpLink.href = fileUrl;
  tmpLink.download = fileSelectionChoice.value;
  tmpLink.click();

  URL.revokeObjectURL(fileUrl);
}

</script>

<template>
  <UDashboardPanel id="outputProduct" :ui="{ body: 'lg:py-12' }">

    <template #header>
      <UHeader :title="outputProductDefDisplayName" class="mt-2">
        <template #right>
          <USelect
            v-model="fileSelectionChoice"
            :items="fileSelectionOptions"
            :disabled="outputProductDef.filenames.length == 0"
            data-testid="output-product-def-select"
          />
          <UButton
            color="secondary"
            @click="downloadFile"
            :disabled="outputProductDef.filenames.length == 0"
            data-testid="output-product-download"
          >
            Download
          </UButton>
        </template>
      </UHeader>
    </template>

    <template #body>
      <ProsePre
        v-if="viewMode === 'plaintext'"
        :code="fileContentsToDisplay"
        data-testid="output-product-file-contents"
      >
        {{fileContentsToDisplay}}
      </ProsePre>
      <UTable
        v-if="viewMode === 'table'"
        :data="fileContentTableRowsToDisplay"
        :columns="fileContentTableColumnsToDisplay"
        class="flex-1"
      />
    </template>
  </UDashboardPanel>
</template>
