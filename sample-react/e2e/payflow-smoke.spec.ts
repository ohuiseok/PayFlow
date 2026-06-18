import { expect, test } from '@playwright/test';

test('dummy MVP flow reaches parent and child workspaces', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });
  const byTestId = (testID: string) => page.getByTestId(testID).filter({ visible: true }).first();

  await page.goto('/');
  await byTestId('login-submit-button').click();

  await expect(byText('Connect a child')).toBeVisible();
  await byText('Connect child').click();

  await expect(byTestId('parent-home-charge-button')).toBeVisible();
  await expect(byTestId('parent-home-create-mission-button')).toBeVisible();
  await expect(byTestId('parent-home-approval-button')).toBeVisible();

  await byTestId('parent-home-switch-child-button').click();
  await expect(byTestId('child-home-bank-register-button')).toBeVisible();
  await expect(byTestId('child-home-withdrawal-button')).toBeVisible();
});
