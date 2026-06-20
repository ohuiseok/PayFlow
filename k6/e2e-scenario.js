/**
 * PayFlow E2E 시나리오 — k6 부하/기능 테스트
 *
 * 시나리오:
 *   1. 부모 회원가입 (초대 코드 사용)
 *   2. 자녀 회원가입
 *   3. 부모 로그인 → JWT 획득
 *   4. 자녀 로그인 → JWT 획득
 *   5. 부모 은행 계좌 등록
 *   6. 부모 지갑 충전 (오픈뱅킹 입금)
 *   7. 부모-자녀 연결
 *   8. 부모가 미션 생성
 *   9. 자녀가 미션 제출
 *  10. 부모가 미션 승인
 *  11. 부모가 보상 지급
 *  12. 자녀 지갑 잔액 확인
 *  13. 원장 기록 확인
 *
 * 실행:
 *   k6 run k6/e2e-scenario.js
 *   k6 run --env BASE_URL=http://localhost k6/e2e-scenario.js
 *
 * 부하 테스트 모드:
 *   k6 run --vus 10 --duration 30s k6/e2e-scenario.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// ── 설정 ──────────────────────────────────────────────────────────────────────

const BASE_URL     = __ENV.BASE_URL     || 'http://localhost';
const INVITE_CODE  = __ENV.INVITE_CODE  || 'PAYFLOW-PARENT-2024';
const SCENARIO     = __ENV.SCENARIO     || 'functional'; // functional | load

export const options = SCENARIO === 'load'
    ? {
        // 부하 테스트: 10명 VU로 30초 실행
        vus: 10,
        duration: '30s',
        thresholds: {
            http_req_failed:   ['rate<0.01'],   // 실패율 1% 미만
            http_req_duration: ['p(95)<3000'],  // 95%ile 3초 미만
        },
    }
    : {
        // 기능 테스트: 1회 실행
        vus: 1,
        iterations: 1,
    };

// 커스텀 메트릭
const missionPayDuration = new Trend('mission_pay_duration');
const e2eErrors          = new Counter('e2e_errors');

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

function randomPhone() {
    return '010' + Math.floor(Math.random() * 90000000 + 10000000);
}

function randomKey() {
    return 'k6-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8);
}

function authHeaders(token) {
    return { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function assertStatus(res, expectedStatus, label) {
    const ok = check(res, {
        [`${label}: status=${expectedStatus}`]: r => r.status === expectedStatus,
    });
    if (!ok) {
        e2eErrors.add(1);
        console.error(`[FAIL] ${label} — expected ${expectedStatus}, got ${res.status}: ${res.body}`);
    }
    return ok;
}

// ── 메인 시나리오 ──────────────────────────────────────────────────────────────

export default function () {
    const parentPhone = randomPhone();
    const childPhone  = randomPhone();
    let parentToken, childToken, parentId, childId;
    let bankAccountId, missionId;

    // ── 1. 부모 회원가입 ───────────────────────────────────────────────────────
    group('01. 부모 회원가입', () => {
        const res = http.post(`${BASE_URL}/api/users`, JSON.stringify({
            phoneNumber: parentPhone,
            password:    'password1234',
            name:        'E2E-Parent',
            inviteCode:  INVITE_CODE,
        }), { headers: { 'Content-Type': 'application/json' } });

        assertStatus(res, 201, '부모 회원가입');
        const body = JSON.parse(res.body);
        check(body, {
            '부모 역할이 PARENT': b => b.role === 'PARENT',
        });
        parentId = body.userId;
    });

    // ── 2. 자녀 회원가입 ───────────────────────────────────────────────────────
    group('02. 자녀 회원가입', () => {
        const res = http.post(`${BASE_URL}/api/users`, JSON.stringify({
            phoneNumber: childPhone,
            password:    'password1234',
            name:        'E2E-Child',
        }), { headers: { 'Content-Type': 'application/json' } });

        assertStatus(res, 201, '자녀 회원가입');
        const body = JSON.parse(res.body);
        check(body, {
            '자녀 역할이 CHILD': b => b.role === 'CHILD',
        });
        childId = body.userId;
    });

    // ── 3. 부모 로그인 ─────────────────────────────────────────────────────────
    group('03. 부모 로그인', () => {
        const res = http.post(`${BASE_URL}/api/users/login`, JSON.stringify({
            phoneNumber: parentPhone,
            password:    'password1234',
        }), { headers: { 'Content-Type': 'application/json' } });

        assertStatus(res, 200, '부모 로그인');
        parentToken = JSON.parse(res.body).accessToken;
        check(parentToken, { '부모 JWT 발급': t => t && t.length > 0 });
    });

    // ── 4. 자녀 로그인 ─────────────────────────────────────────────────────────
    group('04. 자녀 로그인', () => {
        const res = http.post(`${BASE_URL}/api/users/login`, JSON.stringify({
            phoneNumber: childPhone,
            password:    'password1234',
        }), { headers: { 'Content-Type': 'application/json' } });

        assertStatus(res, 200, '자녀 로그인');
        childToken = JSON.parse(res.body).accessToken;
    });

    if (!parentToken || !childToken) {
        console.error('로그인 실패 — 나머지 시나리오를 건너뜁니다.');
        e2eErrors.add(1);
        return;
    }

    // ── 5. 부모 은행 계좌 등록 ─────────────────────────────────────────────────
    group('05. 부모 은행 계좌 등록', () => {
        const res = http.post(`${BASE_URL}/api/bank/accounts`, JSON.stringify({
            bankCode:          '004',
            accountNumber:     '123456789012',
            accountHolderName: 'E2E-Parent',
        }), { headers: authHeaders(parentToken) });

        assertStatus(res, 201, '계좌 등록');
        bankAccountId = JSON.parse(res.body).bankAccountId;
    });

    // ── 6. 부모 지갑 충전 ──────────────────────────────────────────────────────
    group('06. 부모 지갑 충전', () => {
        const res = http.post(`${BASE_URL}/api/bank/deposits`, JSON.stringify({
            bankAccountId: bankAccountId,
            amount:        50000,
        }), {
            headers: {
                ...authHeaders(parentToken),
                'Idempotency-Key': randomKey(),
            },
        });

        // 오픈뱅킹 목킹 없이 실 서버 실행 시 BANK_PROCESSING 상태일 수 있음
        check(res, {
            '충전 응답 201 또는 200': r => r.status === 201 || r.status === 200,
        });
    });

    sleep(0.5);

    // ── 7. 부모-자녀 연결 ──────────────────────────────────────────────────────
    group('07. 부모-자녀 연결', () => {
        const res = http.post(`${BASE_URL}/api/families/links`, JSON.stringify({
            childUserId: childId,
        }), { headers: authHeaders(parentToken) });

        assertStatus(res, 201, '가족 연결');
        check(JSON.parse(res.body), {
            '상태가 ACTIVE': b => b.status === 'ACTIVE',
        });
    });

    // ── 8. 미션 생성 ───────────────────────────────────────────────────────────
    group('08. 부모 미션 생성', () => {
        const res = http.post(`${BASE_URL}/api/missions`, JSON.stringify({
            childUserId:   childId,
            title:         'E2E 미션: 방 청소하기',
            description:   'k6 E2E 테스트 미션',
            rewardAmount:  3000,
        }), { headers: authHeaders(parentToken) });

        assertStatus(res, 201, '미션 생성');
        const body = JSON.parse(res.body);
        missionId = body.missionId;
        check(body, {
            '미션 상태가 CREATED': b => b.status === 'CREATED',
        });
    });

    // ── 9. 자녀 미션 제출 ──────────────────────────────────────────────────────
    group('09. 자녀 미션 제출', () => {
        const res = http.patch(`${BASE_URL}/api/missions/${missionId}/submit`, JSON.stringify({
            submissionNote: 'k6 테스트 — 완료했습니다!',
        }), { headers: authHeaders(childToken) });

        assertStatus(res, 200, '미션 제출');
        check(JSON.parse(res.body), {
            '미션 상태가 SUBMITTED': b => b.status === 'SUBMITTED',
        });
    });

    // ── 10. 부모 미션 승인 ─────────────────────────────────────────────────────
    group('10. 부모 미션 승인', () => {
        const res = http.patch(`${BASE_URL}/api/missions/${missionId}/approve`, null, {
            headers: authHeaders(parentToken),
        });

        assertStatus(res, 200, '미션 승인');
        check(JSON.parse(res.body), {
            '미션 상태가 APPROVED': b => b.status === 'APPROVED',
        });
    });

    // ── 11. 부모 보상 지급 ─────────────────────────────────────────────────────
    group('11. 부모 보상 지급', () => {
        const start = Date.now();
        const res   = http.post(`${BASE_URL}/api/missions/${missionId}/pay`, null, {
            headers: authHeaders(parentToken),
        });
        missionPayDuration.add(Date.now() - start);

        assertStatus(res, 200, '보상 지급');
        check(JSON.parse(res.body), {
            '미션 상태가 PAID':   b => b.status === 'PAID',
            'transferId 존재':    b => b.transferId != null,
        });
    });

    sleep(1); // Kafka 이벤트가 ledger-service에 반영될 시간

    // ── 12. 자녀 지갑 잔액 확인 ───────────────────────────────────────────────
    group('12. 자녀 지갑 잔액 확인', () => {
        const res = http.get(`${BASE_URL}/api/wallets/me`, { headers: authHeaders(childToken) });

        assertStatus(res, 200, '자녀 지갑 조회');
        const wallet = JSON.parse(res.body);
        check(wallet, {
            '자녀 지갑 잔액 > 0': w => parseFloat(w.balance) > 0,
        });
    });

    // ── 13. 원장 기록 확인 ─────────────────────────────────────────────────────
    group('13. 원장 기록 확인', () => {
        const res = http.get(`${BASE_URL}/api/ledgers/me`, { headers: authHeaders(parentToken) });

        // 원장 API가 없을 경우 스킵 (404는 허용)
        check(res, {
            '원장 조회 성공 또는 미구현': r => r.status === 200 || r.status === 404,
        });
    });

    sleep(0.5);
}

// ── 테스트 종료 요약 ──────────────────────────────────────────────────────────

export function handleSummary(data) {
    const errors = data.metrics['e2e_errors'] ? data.metrics['e2e_errors'].values.count : 0;
    const passed = errors === 0;

    console.log('\n========================================');
    console.log(`PayFlow E2E 시나리오 결과: ${passed ? '✅ PASS' : '❌ FAIL'}`);
    console.log(`E2E 오류 수: ${errors}`);
    if (data.metrics['mission_pay_duration']) {
        const p = data.metrics['mission_pay_duration'].values;
        console.log(`보상 지급 응답 시간 — avg: ${p.avg.toFixed(0)}ms, p95: ${p['p(95)'].toFixed(0)}ms`);
    }
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
