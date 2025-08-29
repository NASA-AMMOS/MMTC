<script setup lang="ts">
// import type { ConfigFile } from '@/services/mmtc-api.ts';

import OutputFileViewer from "../../components/OutputFileViewer.vue";

const route = useRoute()
const outputProductDefName = computed(() => route.params.slug as string)
</script>

<template>
  <OutputFileViewer :outputProductDefName="outputProductDefName"/>
</template>
