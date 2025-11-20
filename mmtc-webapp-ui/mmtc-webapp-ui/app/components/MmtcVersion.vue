<script setup lang="ts">
import type { MmtcVersion } from '@/services/mmtc-api.ts';
import { retrieveMmtcInfo } from '@/services/mmtc-api';

defineProps<{

}>()

const mmtcVersion = ref<MmtcVersion>({
  version: '',
  buildDate: '',
  commit: ''
})

onMounted(async () => {
  const mmtcInfo = await retrieveMmtcInfo();
  mmtcVersion.value = mmtcInfo.mmtcVersion;
})
</script>

<template>
  <p class="text-xs">
  MMTC {{ mmtcVersion.version }} ({{mmtcVersion.commit}})
  </p>
</template>
