<script setup lang="ts">
import type { MmtcVersion } from '@/services/mmtc-api.ts';
import { retrieveMmtcVersion } from '@/services/mmtc-api';

defineProps<{

}>()

const mmtcVersion = ref<MmtcVersion>({
  version: '',
  buildDate: '',
  commit: ''
})

onMounted(async () => {
  mmtcVersion.value = await retrieveMmtcVersion();
})
</script>

<template>
  <p class="text-xs">
  MMTC {{ mmtcVersion.version }} ({{mmtcVersion.commit}})
  </p>
</template>
