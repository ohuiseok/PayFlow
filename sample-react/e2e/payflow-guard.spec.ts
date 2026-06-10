import { expect, test } from '@playwright/test';

test('family-linked guard keeps parent on linking screen before home access', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });

  await page.goto('/');
  await byText('로그인', { exact: true }).click();

  await expect(byText('자녀와 연결하기')).toBeVisible();
  await expect(byText('오늘의 보상 흐름')).toHaveCount(0);

  await byText('요청 표시시키기').click();
  await byText('승인', { exact: true }).click();

  await expect(byText('오늘의 보상 흐름')).toBeVisible();
});
