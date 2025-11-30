<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import type { OutputProductDef } from '@/services/mmtc-api.ts';
import { retrieveOutputProductDefs } from '@/services/mmtc-api';

const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

const outputProductDefs = ref([])

const leftSidebarMainLinks = ref([]) satisfies NavigationMenuItem[]

const leftSidebarBottomLinks =
[
  {
    label: 'Documentation',
    icon: 'i-lucide-info',
    to: 'https://github.com/nuxt-ui-templates/dashboard',
    target: '_blank'
  },
  {
    label: 'MMTC on GitHub',
    icon: 'i-lucide-square-arrow-up-right',
    to: 'https://github.com/NASA-AMMOS/MMTC',
    target: '_blank'
  }
] satisfies NavigationMenuItem[]

const appMenuItems = [
  {
    label: 'Exit',
    icon: 'i-lucide-x',
    color: 'error'
  }
] satisfies DropdownMenuItem[]

onMounted(async () => {
  outputProductDefs.value = await retrieveOutputProductDefs();
})

watch(outputProductDefs, async (newOutputProductDefs, oldOutputProductDefs) => {
  leftSidebarMainLinks.value.push({
    label: 'Time Correlation',
    icon: 'i-lucide-chart-line',
    to: '/'
  });

  const outputProductMenuItems = []

  newOutputProductDefs.forEach((def) => {
    outputProductMenuItems.push({
      label: def.displayName,
      to: '/outputs/' + def.name
    })
  });

  leftSidebarMainLinks.value.push({
    label: 'Outputs',
    // to: '/Outputs',
    icon: 'i-lucide-file-text',
    defaultOpen: true,
    type: 'trigger',
    children: outputProductMenuItems
  });

  leftSidebarMainLinks.value.push({
    label: 'Configuration',
    icon: 'i-lucide-wrench',
    to: '/configuration'
  });
})

function leftSidebarToggle() {
  leftCollapsed.value = !leftCollapsed.value;
  return true;
}

function rightSidebarToggle() {
  rightCollapsed.value = !rightCollapsed.value;
  return true;
}

</script>

<template>
  <UDashboardGroup unit="rem">
    <UDashboardSidebar
      id="leftSidebar"
      side="left"
      v-model:collapsed="leftCollapsed"
      collapsible
      resizable
      :minSize='15'
      class="bg-elevated/25"
      :ui="{ footer: 'lg:border-t lg:border-default' }"
    >
      <template #default="{ collapsed }">
        <span>
          <span v-if="!collapsed">
            <UHeader>
              <template #title>
                <UIcon name="i-lucide-clock-fading" class="size-8" />
                <strong>&nbsp;MMTC</strong>
              </template>
              <template #default>

              </template>
              <template #right>
                <UButton icon="i-lucide-sidebar-close" size="md" color="neutral" variant="outline" @click="leftSidebarToggle"></UButton>
              </template>
            </UHeader>
          </span>
        </span>
        <span v-if="collapsed">
          <UIcon name="i-lucide-clock-fading" class="size-8" />
          <UButton icon="i-lucide-sidebar-open" size="md" color="neutral" variant="outline" @click="leftSidebarToggle" class="mt-2"></UButton>
        </span>

        <UNavigationMenu
          :collapsed="collapsed"
          :items="leftSidebarMainLinks"
          orientation="vertical"
          tooltip
          popover
        />

        <UNavigationMenu
          :collapsed="collapsed"
          :items="leftSidebarBottomLinks"
          orientation="vertical"
          tooltip
          class="mt-auto"
        />
      </template>
      <template #footer="{ collapsed }">
        <span v-if="!collapsed">
          <MmtcVersion/>
        </span>
      </template>
    </UDashboardSidebar>

    <slot />

    <NotificationsSlideover />
    <!--
    <UDashboardSidebar
      id="rightSidebar"
      side="right"
      v-model:collapsed="rightCollapsed"
      collapsible
      resizable
      minSize=15
      class="bg-elevated/25"
      :ui="{ footer: 'lg:border-t lg:border-default' }"
    >

      <template #default="{ collapsed }">
        <span>
          <span v-if="!collapsed">
            <UHeader>
              <template #left>
                <UButton icon="i-lucide-sidebar-open" size="md" color="neutral" variant="outline" @click="rightSidebarToggle"></UButton>
              </template>
              <template #default>
                &nbsp;
              </template>
              <template #right>
                <UDropdownMenu :items="appMenuItems">
                  <UButton label="Menu" color="neutral" variant="outline" icon="i-lucide-square-menu" />
                </UDropdownMenu>
              </template>

            </UHeader>
          </span>
        </span>
        <span v-if="collapsed">
          <UDropdownMenu :items="appMenuItems">
                <UButton color="neutral" variant="outline" icon="i-lucide-square-menu" />
          </UDropdownMenu>
          <UButton icon="i-lucide-sidebar-close" size="md" color="neutral" variant="outline" @click="rightSidebarToggle" class="mt-2"></UButton>
        </span>

        <span class="">Correlation History</span>
        <hr/>

      </template>
    </UDashboardSidebar>
    -->
  </UDashboardGroup>
</template>
