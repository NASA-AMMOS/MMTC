<script setup lang="ts">
import type { MmtcVersion } from '@/services/mmtc-api.ts';
import { retrieveMmtcInfo } from '@/services/mmtc-api';

const colorMode = useColorMode()

const color = computed(() => colorMode.value === 'dark' ? '#1b1718' : 'white')

useHead({
  meta: [
    { charset: 'utf-8' },
    { name: 'viewport', content: 'width=device-width, initial-scale=1' },
    { key: 'theme-color', name: 'theme-color', content: color }
  ],
  link: [
    { rel: 'icon', href: '/favicon.ico' }
  ],
  htmlAttrs: {
    lang: 'en'
  }
})

const title = ref('MMTC')
const description = 'Multi-Mission Time Correlation'

useSeoMeta({
  title: () => title,
  description,
  ogTitle: title,
  ogDescription: description,
  ogImage: 'https://ui.nuxt.com/assets/templates/nuxt/dashboard-light.png',
  twitterImage: 'https://ui.nuxt.com/assets/templates/nuxt/dashboard-light.png',
  twitterCard: 'summary_large_image'
})

onMounted(async () => {
  const mmtcInfo = await retrieveMmtcInfo();
  title.value = `MMTC - ${mmtcInfo.missionName}`;
})

const toaster = { position: 'top-right' }

</script>

<template>
  <UApp :toaster="toaster">
    <NuxtLoadingIndicator />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
