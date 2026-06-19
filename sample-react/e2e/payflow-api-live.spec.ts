import { expect, test } from '@playwright/test';

function authHeaders(token?: string, idempotencyKey?: string) {
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
  };
}

test('live API supports signup, wallet charge, family link, and mission payment', async ({ request }) => {
  const suffix = Date.now().toString().slice(-8);
  const password = 'password12';
  const parentPhone = `0107${suffix}`;
  const childPhone = `0108${suffix}`;

  const parentSignup = await request.post('/api/users', {
    data: {
      phoneNumber: parentPhone,
      password,
      name: 'Live Parent',
      role: 'PARENT',
    },
  });
  expect(parentSignup.ok()).toBeTruthy();
  const parent = await parentSignup.json();

  const childSignup = await request.post('/api/users', {
    data: {
      phoneNumber: childPhone,
      password,
      name: 'Live Child',
      role: 'CHILD',
    },
  });
  expect(childSignup.ok()).toBeTruthy();
  const child = await childSignup.json();

  const parentLogin = await request.post('/api/users/login', {
    data: { phoneNumber: parentPhone, password },
  });
  expect(parentLogin.ok()).toBeTruthy();
  const parentToken = (await parentLogin.json()).accessToken as string;

  const childLogin = await request.post('/api/users/login', {
    data: { phoneNumber: childPhone, password },
  });
  expect(childLogin.ok()).toBeTruthy();
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
  expect(bankAccountResponse.ok()).toBeTruthy();
  const bankAccount = await bankAccountResponse.json();

  const chargeResponse = await request.post('/api/bank/deposits', {
    headers: authHeaders(parentToken, `live-charge-${suffix}`),
    data: {
      bankAccountId: bankAccount.bankAccountId,
      amount: 50000,
    },
  });
  expect(chargeResponse.ok()).toBeTruthy();
  const charge = await chargeResponse.json();
  expect(['SUCCEEDED', 'COMPLETED']).toContain(charge.status);

  const linkResponse = await request.post('/api/families/links', {
    headers: authHeaders(parentToken),
    data: { childUserId: child.userId },
  });
  expect(linkResponse.ok()).toBeTruthy();

  const parentsResponse = await request.get('/api/families/parents', {
    headers: authHeaders(childToken),
  });
  expect(parentsResponse.ok()).toBeTruthy();
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
  expect(missionResponse.ok()).toBeTruthy();
  const mission = await missionResponse.json();

  const submitResponse = await request.patch(`/api/missions/${mission.missionId}/submit`, {
    headers: authHeaders(childToken),
    data: { submissionNote: 'Done' },
  });
  expect(submitResponse.ok()).toBeTruthy();

  const approveResponse = await request.patch(`/api/missions/${mission.missionId}/approve`, {
    headers: authHeaders(parentToken),
  });
  expect(approveResponse.ok()).toBeTruthy();

  const payResponse = await request.post(`/api/missions/${mission.missionId}/pay`, {
    headers: authHeaders(parentToken),
  });
  expect(payResponse.ok()).toBeTruthy();
  expect((await payResponse.json()).status).toBe('PAID');

  const childSummaryResponse = await request.get(`/api/cashbook/children/${child.userId}/summary`, {
    headers: authHeaders(childToken),
  });
  expect(childSummaryResponse.ok()).toBeTruthy();
  expect((await childSummaryResponse.json()).walletBalance).toBeGreaterThanOrEqual(3000);
});
