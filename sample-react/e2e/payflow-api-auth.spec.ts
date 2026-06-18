import { expect, test } from '@playwright/test';

test('API mode uses auth and family link endpoints', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });
  const byTestId = (testID: string) => page.getByTestId(testID).filter({ visible: true }).first();

  await page.route('**/api/users/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'mock-access-token',
        user: {
          userId: 1,
          name: 'API Parent',
          role: 'PARENT',
          status: 'ACTIVE',
        },
      }),
    });
  });

  await page.route('**/api/families/links', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        familyLinkId: 10,
        parentUserId: 1,
        childUserId: 2,
        status: 'ACTIVE',
      }),
    });
  });

  await page.route('**/api/missions', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  await page.goto('/');
  await byTestId('login-submit-button').click();

  await expect(byText('Connect a child')).toBeVisible();
  await byText('Connect child').click();

  await expect(byTestId('parent-home-charge-button')).toBeVisible();
});
