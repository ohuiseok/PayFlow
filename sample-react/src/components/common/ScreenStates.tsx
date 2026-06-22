import { Body } from '../common';

export function LoadingState({ title = '불러오는 중', body }: { title?: string; body: string }) {
  return <Body>{`${title} ${body}`}</Body>;
}

export function EmptyState({ title, body }: { title?: string; body: string }) {
  if (title) {
    return <Body>{`${title} ${body}`}</Body>;
  }

  return <Body>{body}</Body>;
}
