import { Platform } from 'react-native';

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      requestPayment: (method: string, options: Record<string, unknown>) => Promise<void>;
    };
  }
}

let loadingPromise: Promise<void> | null = null;

function loadTossScript() {
  if (Platform.OS !== 'web') {
    return Promise.reject(new Error('Toss widget is available on web in this sample app.'));
  }
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Toss widget requires a browser environment.'));
  }
  if (window.TossPayments) {
    return Promise.resolve();
  }
  if (loadingPromise) {
    return loadingPromise;
  }

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://js.tosspayments.com/v1/payment';
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Failed to load Toss Payments SDK.'));
    document.head.appendChild(script);
  });
  return loadingPromise;
}

export async function requestTossWidgetPayment(input: {
  clientKey: string;
  amount: number;
  orderId: string;
  orderName: string;
  customerName: string;
}) {
  await loadTossScript();
  const tossPayments = window.TossPayments?.(input.clientKey);
  if (!tossPayments) {
    throw new Error('Toss Payments SDK is not ready.');
  }

  const origin = window.location.origin;
  await tossPayments.requestPayment('카드', {
    amount: input.amount,
    orderId: input.orderId,
    orderName: input.orderName,
    customerName: input.customerName,
    successUrl: `${origin}/parent/credit-charge`,
    failUrl: `${origin}/parent/credit-charge`,
  });
}
