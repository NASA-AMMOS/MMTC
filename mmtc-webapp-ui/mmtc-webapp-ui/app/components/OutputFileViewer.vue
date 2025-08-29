<script setup lang="ts">
// import type { ConfigFile } from '@/services/mmtc-api.ts';
import type { OutputProductDef } from '@/services/mmtc-api.ts';
import { retrieveOutputProductDefs, retrieveOutputProductFileContents } from '@/services/mmtc-api';

const props = defineProps(['outputProductDefName'])

const allOutputProductDefs = ref([])
const outputProductDef = ref({})

const fileSelectionOptions = ref([])
const fileSelectionChoice = ref('')

const fileContentsToDisplay = ref('No file selected.')

watch([outputProductDef, fileSelectionChoice], async (newFileSelectionChoice, oldFileSelectionChoice) => {
  updateFileContents();
})

async function updateFileContents() {
  if (fileSelectionChoice.value !== '') {
    const fileContents = await retrieveOutputProductFileContents(props.outputProductDefName, fileSelectionChoice.value)
    fileContentsToDisplay.value = fileContents;
  } else {
    fileContentsToDisplay.value = 'No file selected.';
  }
}

async function updateSelectedProductDef() {
  allOutputProductDefs.value.forEach((def) => {
    if (def.name === props.outputProductDefName) {
      outputProductDef.value = def
    }
  })

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

</script>

<template>
  <UDashboardPanel id="outputProduct" :ui="{ body: 'lg:py-12' }">

    <template #header>
      <UHeader :title="props.outputProductDefName" class="mt-2">
        <template #right>
          <USelect v-model="fileSelectionChoice" :items="fileSelectionOptions"/>
        </template>
      </UHeader>
    </template>

    <template #body>
      <ProsePre :code="fileContentsToDisplay">
        {{fileContentsToDisplay}}
      </ProsePre>
    </template>
  </UDashboardPanel>
</template>
