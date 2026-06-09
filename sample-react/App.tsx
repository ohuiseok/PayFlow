import { NavigationContainer } from '@react-navigation/native';
import { QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';

import { AppStateProvider } from './src/state/AppState';
import { AppNavigator } from './src/navigation/AppNavigator';
import { queryClient } from './src/query/queryClient';

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppStateProvider>
        <NavigationContainer>
          <StatusBar style="dark" />
          <AppNavigator />
        </NavigationContainer>
      </AppStateProvider>
    </QueryClientProvider>
  );
}
