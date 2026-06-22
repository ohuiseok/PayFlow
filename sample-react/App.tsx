import { NavigationContainer } from '@react-navigation/native';
import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, Text, TextInput, View } from 'react-native';

import { AppStateProvider } from './src/state/AppState';
import { AppNavigator } from './src/navigation/AppNavigator';
import { linking } from './src/navigation/routes';
import { queryClient } from './src/query/queryClient';

const fontFamily = 'Pretendard';
let globalFontApplied = false;

function applyGlobalFont() {
  if (globalFontApplied) {
    return;
  }

  const textDefaults = Text as typeof Text & { defaultProps?: Record<string, unknown> };
  const inputDefaults = TextInput as typeof TextInput & { defaultProps?: Record<string, unknown> };

  textDefaults.defaultProps = textDefaults.defaultProps ?? {};
  textDefaults.defaultProps.style = [textDefaults.defaultProps.style, { fontFamily }];

  inputDefaults.defaultProps = inputDefaults.defaultProps ?? {};
  inputDefaults.defaultProps.style = [inputDefaults.defaultProps.style, { fontFamily }];
  globalFontApplied = true;
}

export default function App() {
  const [fontsLoaded] = useFonts({
    [fontFamily]: require('./assets/Pretendard.otf'),
  });

  if (!fontsLoaded) {
    return (
      <View style={{ alignItems: 'center', flex: 1, justifyContent: 'center' }}>
        <ActivityIndicator />
      </View>
    );
  }

  applyGlobalFont();

  return (
    <QueryClientProvider client={queryClient}>
      <AppStateProvider>
        <NavigationContainer linking={linking}>
          <StatusBar style="dark" />
          <AppNavigator />
        </NavigationContainer>
      </AppStateProvider>
    </QueryClientProvider>
  );
}
