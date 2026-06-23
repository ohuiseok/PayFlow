import { expect, test } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';

const OUT_DIR = path.join(__dirname, '..', '..', 'screenshots');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ss = (name: string) => path.join(OUT_DIR, `${name}.png`);

// Viewport: narrow so the app looks like a mobile card
// The app renders as a centered card on desktop

test('capture all screens', async ({ page }) => {
  await page.setViewportSize({ width: 430, height: 870 });

  // ── 1. Login ──────────────────────────────────────────────
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('01_login'), fullPage: false });

  // ── 2. Signup ─────────────────────────────────────────────
  await page.goto('/signup');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('02_signup'), fullPage: false });

  // ── 3. Dummy login → ParentFamilyLink ─────────────────────
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  await page.getByPlaceholder('휴대폰 번호').fill('01012345678');
  await page.getByPlaceholder('비밀번호').fill('password1');
  await page.getByTestId('login-submit-button').click();

  // Wait for family link or home screen
  await page.waitForURL(/parent\/(family-link|home)/, { timeout: 10000 });

  if (page.url().includes('family-link')) {
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: ss('03_parent_family_link'), fullPage: false });

    // Connect child to proceed to parent home
    await page.getByText('Connect child').click();
    await page.waitForURL(/parent\/home/, { timeout: 10000 });
  }

  // ── 4. Parent Home ────────────────────────────────────────
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('04_parent_home'), fullPage: false });

  // ── 5. Credit Charge ──────────────────────────────────────
  await page.getByTestId('parent-home-charge-button').click();
  await page.waitForURL(/credit-charge/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('05_credit_charge'), fullPage: false });
  await page.goBack();

  // ── 6. Parent Withdrawal ──────────────────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.getByTestId('parent-home-withdrawal-button').click();
  await page.waitForURL(/withdrawal/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('06_parent_withdrawal'), fullPage: false });
  await page.goBack();

  // ── 7. Parent Approval ────────────────────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.getByTestId('parent-home-approval-button').click();
  await page.waitForURL(/approvals/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('07_parent_approval'), fullPage: false });
  await page.goBack();

  // ── 8. Mission Create ─────────────────────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.getByTestId('parent-home-create-mission-button').click();
  await page.waitForURL(/missions\/new/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('08_mission_create'), fullPage: false });
  await page.goBack();

  // ── 9. Parent Credit History ──────────────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.getByTestId('parent-home-credit-history-button').click();
  await page.waitForURL(/credit-history/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('09_credit_history'), fullPage: false });
  await page.goBack();

  // ── 10. Parent Family Link (re-access) ────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.getByTestId('parent-home-family-link-button').click();
  await page.waitForURL(/family-link/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('10_parent_family_link2'), fullPage: false });
  await page.goto('/parent/home');

  // ── 11. Switch to Child → Child Home ─────────────────────
  await page.waitForURL(/parent\/home/, { timeout: 5000 });
  await page.waitForLoadState('networkidle');
  await page.getByTestId('parent-home-switch-child-button').click();
  await page.waitForURL(/child\/home/, { timeout: 10000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('11_child_home'), fullPage: false });

  // ── 12. Child Withdrawal ──────────────────────────────────
  await page.getByTestId('child-home-withdrawal-button').click();
  await page.waitForURL(/child\/withdrawal/, { timeout: 8000 });
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('12_child_withdrawal'), fullPage: false });
  await page.goBack();

  // ── 13. Bank Account Register ─────────────────────────────
  await page.waitForURL(/child\/home/, { timeout: 5000 });
  // bank register button may not exist if bank already registered; navigate directly
  try {
    await page.getByTestId('child-home-bank-register-button').click({ timeout: 3000 });
    await page.waitForURL(/bank-account/, { timeout: 8000 });
  } catch {
    await page.goto('/child/bank-account');
  }
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('13_bank_account'), fullPage: false });
  await page.goto('/child/home');

  // ── 14. Child Cashbook ────────────────────────────────────
  await page.waitForLoadState('networkidle');
  // Cashbook - navigate directly (no testId button visible but URL exists)
  await page.goto('/child/cashbook');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('14_child_cashbook'), fullPage: false });

  // ── 15. Mission Submit ────────────────────────────────────
  await page.goto('/child/missions/submit');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('15_mission_submit'), fullPage: false });

  // ── 16. Mission Resubmit ──────────────────────────────────
  await page.goto('/child/missions/resubmit');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('16_mission_resubmit'), fullPage: false });

  // ── 17. Child Invite Code ─────────────────────────────────
  await page.goto('/child/invite-code');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: ss('17_child_invite_code'), fullPage: false });

  console.log('✅ All screenshots saved to:', OUT_DIR);
});
