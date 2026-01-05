<script setup lang="ts">
import {parseIso8601Utc, getAttrTableHtmlFor} from "../services/utils";

const props = defineProps<{
  title: string
  triplet: object
}>()

const tripletHtml = computed(() => {
  const attributes = [];

  attributes.push({key: 'Encoded SCLK', val: props.triplet.encSclk });
  attributes.push({key: 'TDT(G) Cal', val: props.triplet.tdtStr });
  attributes.push({key: 'Clock rate', val: props.triplet.clkChgRate });

  return getAttrTableHtmlFor(attributes);
})

</script>

<template>
  <div class="ml-5">
    <p class="font-semibold text-xs">
      {{props.title}}
    </p>
    <div v-html="tripletHtml" :data-testid="`triplet-display-${props.title}`"/>
  </div>
</template>
