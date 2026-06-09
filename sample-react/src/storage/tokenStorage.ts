import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

const accessTokenKey = 'payflow.accessToken';
let memoryToken: string | null = null;

function canUseLocalStorage() {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

export const tokenStorage = {
  async getAccessToken() {
    try {
      if (Platform.OS === 'web') {
        return canUseLocalStorage() ? window.localStorage.getItem(accessTokenKey) : memoryToken;
      }

      return (await SecureStore.getItemAsync(accessTokenKey)) ?? memoryToken;
    } catch {
      return memoryToken;
    }
  },

  async setAccessToken(token: string) {
    memoryToken = token;

    try {
      if (Platform.OS === 'web') {
        if (canUseLocalStorage()) {
          window.localStorage.setItem(accessTokenKey, token);
        }
        return;
      }

      await SecureStore.setItemAsync(accessTokenKey, token);
    } catch {
      // Keep the in-memory token so the current session can continue.
    }
  },

  async clearAccessToken() {
    memoryToken = null;

    try {
      if (Platform.OS === 'web') {
        if (canUseLocalStorage()) {
          window.localStorage.removeItem(accessTokenKey);
        }
        return;
      }

      await SecureStore.deleteItemAsync(accessTokenKey);
    } catch {
      // Ignore storage cleanup failures; the memory fallback is already cleared.
    }
  },
};
