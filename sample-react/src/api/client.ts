import { appConfig } from '../config/appConfig';
import { tokenStorage } from '../storage/tokenStorage';

type RequestOptions = {
  auth?: boolean;
  headers?: Record<string, string>;
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
};

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(message: string, status: number, body: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

function buildUrl(path: string) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }

  const baseUrl = appConfig.apiBaseUrl.replace(/\/$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${baseUrl}${normalizedPath}`;
}

async function parseResponse(response: Response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function resolveErrorMessage(body: unknown, fallback: string) {
  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }

  return fallback;
}

async function request<T>(path: string, options: RequestOptions = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (options.auth !== false) {
    const token = await tokenStorage.getAccessToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const response = await fetch(buildUrl(path), {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const body = await parseResponse(response);

  if (!response.ok) {
    throw new ApiError(
      resolveErrorMessage(body, `API request failed with status ${response.status}`),
      response.status,
      body,
    );
  }

  return body as T;
}

export const apiClient = {
  get<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return request<T>(path, { ...options, method: 'GET' });
  },

  post<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return request<T>(path, { ...options, method: 'POST', body });
  },

  patch<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return request<T>(path, { ...options, method: 'PATCH', body });
  },

  delete<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return request<T>(path, { ...options, method: 'DELETE' });
  },
};
