import { expect, test } from '@playwright/test';

test('API mode uses auth and family endpoints', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });

  await page.route('**/api/users/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'mock-access-token',
        user: {
          userId: 1,
          name: 'API 지훈',
          role: 'PARENT',
          status: 'ACTIVE',
        },
      }),
    });
  });

  await page.route('**/api/families/invitations', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        invitationId: 10,
        inviteCode: 'PF9999',
        expiresAt: '2026-06-09T22:30:00',
        status: 'ACTIVE',
      }),
    });
  });

  await page.goto('/');

  await expect(byText('미션으로 배우는 돈')).toBeVisible();
  await expect(byText('API 로그인')).toBeVisible();
  await byText('로그인', { exact: true }).click();

  await expect(byText('자녀와 연결하기')).toBeVisible();
  await expect(byText('PF9999')).toBeVisible();
  await expect(byText('가족 연결 확인')).toBeVisible();
});
