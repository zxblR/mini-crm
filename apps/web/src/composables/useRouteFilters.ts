import { useRoute, useRouter } from 'vue-router';

export function useRouteFilters() {
  const route = useRoute();
  const router = useRouter();

  function stringValue(key: string): string | undefined {
    const value = route.query[key];
    return typeof value === 'string' && value.length > 0 ? value : undefined;
  }

  function numberValue(key: string, fallback: number): number {
    const value = Number(stringValue(key));
    return Number.isFinite(value) && value > 0 ? value : fallback;
  }

  function booleanValue(key: string): boolean | undefined {
    const value = stringValue(key);
    if (value === 'true') return true;
    if (value === 'false') return false;
    return undefined;
  }

  async function replaceQuery<T extends object>(state: T): Promise<void> {
    const query = Object.fromEntries(
      Object.entries(state)
        .filter(([, value]) => value !== undefined && value !== null && value !== '')
        .map(([key, value]) => [key, String(value)]),
    );
    await router.replace({ query });
  }

  return { stringValue, numberValue, booleanValue, replaceQuery };
}
