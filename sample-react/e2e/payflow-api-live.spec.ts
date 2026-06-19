import { expect, test } from '@playwright/test';
import type { APIResponse } from '@playwright/test';

function authHeaders(token?: string, idempotencyKey?: string) {
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
  };
}

async function expectOk(response: APIResponse) {
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
}

test('live API supports signup, wallet charge, family link, and mission payment', async ({ request }) => {
  const suffix = Math.floor(10000000 + Math.random() * 90000000).toString();
  const password = 'password12';
  const parentPhone = `010${suffix}`;
  const childPhone = `011${suffix}`;

  const parentSignup = await request.post('/api/users', {
    data: {
      phoneNumber: parentPhone,
      password,
      name: 'Live Parent',
      role: 'PARENT',
    },
  });
  await expectOk(parentSignup);
  const parent = await parentSignup.json();

  const childSignup = await request.post('/api/users', {
    data: {
      phoneNumber: childPhone,
      password,
      name: 'Live Child',
      role: 'CHILD',
    },
  });
  await expectOk(childSignup);
  const child = await childSignup.json();

  const parentLogin = await request.post('/api/users/login', {
    data: { phoneNumber: parentPhone, password },
  });
  await expectOk(parentLogin);
  const parentToken = (await parentLogin.json()).accessToken as string;

  const childLogin = await request.post('/api/users/login', {
    data: { phoneNumber: childPhone, password },
  });
  await expectOk(childLogin);
  const childToken = (await childLogin.json()).accessToken as string;

  const bankAccountResponse = await request.post('/api/bank/accounts', {
    headers: authHeaders(parentToken),
    data: {
      bankCode: '004',
      bankName: 'MOCK_BANK',
      accountNumber: `123456${suffix}`,
      accountHolderName: 'Live Parent',
    },
  });
  await expectOk(bankAccountResponse);
  const bankAccount = await bankAccountResponse.json();

  const chargeResponse = await request.post('/api/bank/deposits', {
    headers: authHeaders(parentToken, `live-charge-${suffix}`),
    data: {
      bankAccountId: bankAccount.bankAccountId,
      amount: 50000,
    },
  });
  await expectOk(chargeResponse);
  const charge = await chargeResponse.json();
  expect(['SUCCEEDED', 'COMPLETED']).toContain(charge.status);

  const linkResponse = await request.post('/api/families/links', {
    headers: authHeaders(parentToken),
    data: { childUserId: child.userId },
  });
  await expectOk(linkResponse);

  const parentsResponse = await request.get('/api/families/parents', {
    headers: authHeaders(childToken),
  });
  await expectOk(parentsResponse);
  expect((await parentsResponse.json()).length).toBeGreaterThan(0);

  const missionResponse = await request.post('/api/missions', {
    headers: authHeaders(parentToken),
    data: {
      childUserId: child.userId,
      title: 'Live mission',
      description: 'Live API mission',
      rewardAmount: 3000,
    },
  });
  await expectOk(missionResponse);
  const mission = await missionResponse.json();

  const submitResponse = await request.patch(`/api/missions/${mission.missionId}/submit`, {
    headers: authHeaders(childToken),
    data: { submissionNote: 'Done' },
  });
  await expectOk(submitResponse);

  const approveResponse = await request.patch(`/api/missions/${mission.missionId}/approve`, {
    headers: authHeaders(parentToken),
  });
  await expectOk(approveResponse);

  const payResponse = await request.post(`/api/missions/${mission.missionId}/pay`, {
    headers: authHeaders(parentToken),
  });
  await expectOk(payResponse);
  expect((await payResponse.json()).status).toBe('PAID');

  const childSummaryResponse = await request.get(`/api/cashbook/children/${child.userId}/summary`, {
    headers: authHeaders(childToken),
  });
  await expectOk(childSummaryResponse);
  expect((await childSummaryResponse.json()).walletBalance).toBeGreaterThanOrEqual(3000);
});
