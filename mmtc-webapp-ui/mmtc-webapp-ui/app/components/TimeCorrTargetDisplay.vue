<script setup lang="ts">
import {getAttrTableHtmlFor} from "../services/utils";

const props = defineProps<{
  correlation: object
}>()

const html = computed(() => {
  const attributes = [];

  attributes.push({key: 'SCLK', val: `${props.correlation.target.targetSample.sclkCoarse}:${props.correlation.target.targetSample.sclkFine}` });
  attributes.push({key: 'Ground Station ID', val: props.correlation.target.targetSampleGroundStationId })
  attributes.push({key: 'OWLT (sec)', val: props.correlation.owlt_sec });
  attributes.push({key: 'ERT', val: props.correlation.target.targetSampleErt})
  attributes.push({key: 'Encoded SCLK', val: props.correlation.target.targetSampleEncSclk });
  attributes.push({key: 'TDT(G)', val: props.correlation.target.targetSampleTdtG });

  return getAttrTableHtmlFor(attributes);
})

</script>

<template>
  <div v-html="html"/>
</template>
