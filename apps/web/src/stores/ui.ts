import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false);
  const mobileSidebarOpen = ref(false);
  const globalLoadingCount = ref(0);
  const pageTitle = ref('工作台');

  const isLoading = computed(() => globalLoadingCount.value > 0);

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }

  function openMobileSidebar(): void {
    mobileSidebarOpen.value = true;
  }

  function closeMobileSidebar(): void {
    mobileSidebarOpen.value = false;
  }

  function setPageTitle(title: string): void {
    pageTitle.value = title;
  }

  function startLoading(): void {
    globalLoadingCount.value += 1;
  }

  function finishLoading(): void {
    globalLoadingCount.value = Math.max(0, globalLoadingCount.value - 1);
  }

  return {
    sidebarCollapsed,
    mobileSidebarOpen,
    globalLoadingCount,
    pageTitle,
    isLoading,
    toggleSidebar,
    openMobileSidebar,
    closeMobileSidebar,
    setPageTitle,
    startLoading,
    finishLoading,
  };
});
