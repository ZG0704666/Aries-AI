<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute } from 'vitepress';
import { disposeMermaid, initMermaid } from './mermaid';

const route = useRoute();

function run(): void {
  void nextTick(() => {
    initMermaid();
    // 路由懒加载的正文可能晚于 Layout 更新，补一次扫描。
    window.setTimeout(initMermaid, 500);
  });
}

onMounted(run);
watch(() => route.path, run);
onBeforeUnmount(disposeMermaid);
</script>

<template>
  <span class="mermaid-mount" aria-hidden="true"></span>
</template>

<style>
.mermaid-mount {
  display: none;
}
</style>
