import { expect, test } from '@playwright/test';

test('family-linked guard keeps parent on linking screen before home access', async ({ page }) => {
  const byText = (text: string, options?: Parameters<typeof page.getByText>[1]) =>
    page.getByText(text, options).filter({ visible: true });
  const byTestId = (testID: string) => page.getByTestId(testID).filter({ visible: true }).first();

  await page.goto('/');
  await byTestId('login-submit-button').click();

  await expect(byText('Connect a child')).toBeVisible();
  await expect(byTestId('parent-home-charge-button')).toHaveCount(0);

  await byText('Connect child').click();

  await expect(byTestId('parent-home-charge-button')).toBeVisible();
});
