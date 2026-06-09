import { NavigationContainer } from '@react-navigation/native';
import { StatusBar } from 'expo-status-bar';

import { AppStateProvider } from './src/state/AppState';
import { AppNavigator } from './src/navigation/AppNavigator';

export default function App() {
  return (
    <AppStateProvider>
      <NavigationContainer>
        <StatusBar style="dark" />
        <AppNavigator />
      </NavigationContainer>
    </AppStateProvider>
  );
}
