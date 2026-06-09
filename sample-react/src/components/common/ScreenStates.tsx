import { Body, InfoBox } from '../common';

export function LoadingState({ title = '불러오는 중', body }: { title?: string; body: string }) {
  return <InfoBox tone="blue" title={title} body={body} />;
}

export function EmptyState({ title, body }: { title?: string; body: string }) {
  if (title) {
    return <InfoBox title={title} body={body} />;
  }

  return <Body>{body}</Body>;
}
