import { expect, test } from '@playwright/test';

test('dummy MVP flow works end to end', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });

  await page.goto('/');

  await expect(byText('미션으로 배우는 돈')).toBeVisible();
  await byText('로그인', { exact: true }).click();

  await expect(byText('자녀와 연결하기')).toBeVisible();
  await byText('요청 표시시키기').click();
  await byText('승인', { exact: true }).click();

  await expect(byText('오늘의 보상 흐름')).toBeVisible();
  await byText('충전', { exact: true }).click();
  await expect(byText('충전 계좌')).toBeVisible();
  await byText('충전하기').click();
  await expect(byText('충전 완료 · 보상 크레딧이 증가했습니다.')).toBeVisible({ timeout: 3000 });
  await byText('부모 홈으로').click();

  await expect(byText('최근 크레딧 기록')).toBeVisible();
  await byText('미션 등록').click();
  await expect(byText('새 미션 만들기')).toBeVisible();
  await byText('미션 등록', { exact: true }).last().click();

  await expect(byText('영어 단어 20개 외우기')).toBeVisible();
  await byText('자녀 홈').click();
  await expect(byText('내 미션과 캐시북')).toBeVisible();
  await byText('영어 단어 20개 외우기').click();
  await expect(byText('미션 완료 알리기')).toBeVisible();
  await byText('제출하기').click();

  await expect(byText('승인 대기').first()).toBeVisible();
  await byText('부모 홈').click();
  await byText('승인', { exact: true }).click();
  await expect(byText('승인할 미션')).toBeVisible();
  await byText('승인', { exact: true }).last().click();
  await expect(byText('승인 완료 · 보상이 지급되었습니다.')).toBeVisible();

  await byText('자녀 홈').click();
  await byText('계좌 등록').click();
  await expect(byText('받을 계좌 연결')).toBeVisible();
  await expect(byText('국민은행')).toBeVisible();
  await byText('신한은행').click();
  await byText('계좌 등록', { exact: true }).last().click();
  await expect(byText('신한은행', { exact: true })).toBeVisible();
  await expect(byText('계좌가 연결되었습니다.')).toBeVisible();
  await byText('출금 화면으로').click();

  await expect(byText('캐시북에서 출금')).toBeVisible();
  await byText('출금 요청', { exact: true }).click();
  await expect(byText('5,000원 출금할까요?')).toBeVisible();
  await byText('출금 진행').click();
  await expect(byText('출금 완료 · 잔액이 차감되었습니다.')).toBeVisible({ timeout: 3000 });
});
