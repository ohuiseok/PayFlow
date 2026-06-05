const trueValues = new Set(['1', 'true', 'yes', 'on']);

function readBoolean(value: string | undefined, fallback = false) {
  if (!value) {
    return fallback;
  }

  return trueValues.has(value.trim().toLowerCase());
}

export const appConfig = {
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080',
  useDummyData: readBoolean(process.env.EXPO_PUBLIC_USE_DUMMY_DATA, true),
};
