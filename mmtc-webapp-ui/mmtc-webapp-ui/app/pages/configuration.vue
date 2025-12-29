<script setup lang="ts">
// import type { ConfigFile } from '@/services/mmtc-api.ts';
import { retrieveConfigurationContent } from '@/services/mmtc-api';

const configurationFileContents = ref([])

const fileSelectionOptions = ref([])
const fileSelectionChoice = ref('')

const configFileContentsToDisplay = ref('```\n```')

watch(fileSelectionChoice, async (newFileSelectionChoice, oldFileSelectionChoice) => {
  if (fileSelectionChoice.value !== '') {
    configurationFileContents.value.forEach((configFile) => {
      if (configFile.filename === fileSelectionChoice.value) {
        configFileContentsToDisplay.value = configFile.contents;
      }
    })
  }
})

onMounted(async () => {
  configurationFileContents.value = await retrieveConfigurationContent();
  fileSelectionOptions.value = configurationFileContents.value.map(obj => obj.filename) as string[];
  fileSelectionChoice.value = fileSelectionOptions.value[0];

})

</script>

<template>
  <UDashboardPanel id="configuration" :ui="{ body: 'lg:py-12' }">

    <template #header>
      <UHeader title="Configuration Viewer" class="mt-2">
        <template #right>
          <USelect v-model="fileSelectionChoice" :items="fileSelectionOptions"/>
        </template>
      </UHeader>
    </template>

    <template #body>
        <ProsePre :code="configFileContentsToDisplay">
          {{configFileContentsToDisplay}}
        </ProsePre>
    </template>
  </UDashboardPanel>
</template>
