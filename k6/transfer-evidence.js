import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const mode = __ENV.MODE || 'concurrent';
const runId = __ENV.RUN_ID || `manual-${Date.now()}`;
const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const resultDir = __ENV.RESULT_DIR || 'results';
const dataFile = __ENV.TEST_USERS_FILE || './test-users.local.json';
const testData = JSON.parse(open(dataFile));
const users = testData.users || [];
const amount = Number(__ENV.AMOUNT || testData.amount || 1000);

if (users.length === 0) {
    throw new Error('test users are required: provide TEST_USERS_FILE');
}

for (const [index, user] of users.entries()) {
    if (!user.token || !user.receiverUserId) {
        throw new Error(`users[${index}] requires token and receiverUserId`);
    }
}

const businessSucceeded = new Counter('business_succeeded');
const businessFailed = new Counter('business_failed');
const compensationRequired = new Counter('compensation_required');
const invalidResponses = new Counter('invalid_responses');
const recoveredAfterTimeout = new Counter('recovered_after_timeout');

function numberEnv(name, fallback) {
    const value = Number(__ENV[name]);
    return Number.isFinite(value) && value > 0 ? value : fallback;
}

function scenarioOptions() {
    if (mode === 'throughput') {
        return {
            transfer: {
                executor: 'constant-arrival-rate',
                rate: numberEnv('RATE', 420),
                timeUnit: '1s',
                duration: __ENV.DURATION || '5m',
                preAllocatedVUs: numberEnv('PRE_ALLOCATED_VUS', 600),
                maxVUs: numberEnv('MAX_VUS', 1000),
            },
        };
    }

    if (mode === 'idempotency') {
        return {
            transfer: {
                executor: 'per-vu-iterations',
                vus: numberEnv('VUS', 100),
                iterations: 1,
                maxDuration: __ENV.MAX_DURATION || '2m',
            },
        };
    }

    return {
        transfer: {
            executor: 'per-vu-iterations',
            vus: numberEnv('VUS', 1000),
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || '2m',
        },
    };
}

export const options = {
    scenarios: scenarioOptions(),
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        invalid_responses: ['count==0'],
        dropped_iterations: ['count==0'],
        business_failed: ['count==0'],
        compensation_required: ['count==0'],
    },
};

function recordBusinessStatus(status) {
    if (status === 'SUCCEEDED') {
        businessSucceeded.add(1);
        return true;
    }
    if (status === 'COMPENSATION_REQUIRED') {
        compensationRequired.add(1);
        return true;
    }
    if (typeof status === 'string') {
        businessFailed.add(1);
        return true;
    }
    return false;
}

function recoverTimedOutTransfer(user, key) {
    for (let attempt = 0; attempt < 10; attempt += 1) {
        sleep(0.5);
        const lookup = http.get(`${baseUrl}/api/transfers/by-idempotency-key`, {
            headers: {
                Authorization: `Bearer ${user.token}`,
                'Idempotency-Key': key,
            },
            tags: { endpoint: 'get-transfer-by-idempotency-key', mode },
        });
        if (lookup.status === 404) {
            continue;
        }
        let body;
        try {
            body = lookup.json();
        } catch (_) {
            return false;
        }
        if (body.status === 'REQUESTED' || body.status === 'PROCESSING') {
            continue;
        }
        if (lookup.status === 200 && recordBusinessStatus(body.status)) {
            recoveredAfterTimeout.add(1);
            return true;
        }
        return false;
    }
    return false;
}

function postTransfer(user, key, attempt) {
    return http.post(
        `${baseUrl}/api/transfers`,
        JSON.stringify({ receiverUserId: Number(user.receiverUserId), amount }),
        {
            headers: {
                Authorization: `Bearer ${user.token}`,
                'Content-Type': 'application/json',
                'Idempotency-Key': key,
            },
            tags: { endpoint: 'create-transfer', mode, attempt: String(attempt) },
            responseCallback: http.expectedStatuses(201, 409),
        },
    );
}

function selectUser() {
    if (mode === 'hot-wallet' || mode === 'idempotency') {
        return users[0];
    }
    return users[exec.scenario.iterationInTest % users.length];
}

function idempotencyKey() {
    if (mode === 'idempotency') {
        return `k6:${runId}:duplicate`;
    }
    return `k6:${runId}:${mode}:${exec.scenario.iterationInTest}`;
}

export default function () {
    const user = selectUser();
    const key = idempotencyKey();
    let response;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
        response = postTransfer(user, key, attempt);
        if (response.status !== 409) {
            break;
        }
        sleep(0.1 * attempt);
    }

    let body;
    try {
        body = response.json();
    } catch (_) {
        body = null;
    }

    const valid = check(response, {
        'HTTP status is 201': (r) => r.status === 201,
        'response contains transfer status': () => body && typeof body.status === 'string',
    });

    if (!valid) {
        invalidResponses.add(1);
        recoverTimedOutTransfer(user, key);
        return;
    }

    recordBusinessStatus(body.status);
}

function metricValue(data, name, key, fallback = 0) {
    const metric = data.metrics[name];
    return metric && metric.values[key] !== undefined ? metric.values[key] : fallback;
}

function evidenceSummary(data) {
    const durationMs = data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : 0;
    const succeeded = metricValue(data, 'business_succeeded', 'count');
    const measuredSeconds = durationMs / 1000;
    return {
        runId,
        mode,
        configuredRate: mode === 'throughput' ? numberEnv('RATE', 420) : null,
        durationMs,
        iterations: metricValue(data, 'iterations', 'count'),
        businessSucceeded: succeeded,
        businessFailed: metricValue(data, 'business_failed', 'count'),
        compensationRequired: metricValue(data, 'compensation_required', 'count'),
        invalidResponses: metricValue(data, 'invalid_responses', 'count'),
        recoveredAfterTimeout: metricValue(data, 'recovered_after_timeout', 'count'),
        droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
        achievedBusinessTps: metricValue(
            data,
            'business_succeeded',
            'rate',
            measuredSeconds > 0 ? succeeded / measuredSeconds : 0,
        ),
        httpFailureRate: metricValue(data, 'http_req_failed', 'rate'),
        responseTimeMs: {
            avg: metricValue(data, 'http_req_duration', 'avg'),
            p90: metricValue(data, 'http_req_duration', 'p(90)'),
            p95: metricValue(data, 'http_req_duration', 'p(95)'),
            p99: metricValue(data, 'http_req_duration', 'p(99)'),
            max: metricValue(data, 'http_req_duration', 'max'),
        },
    };
}

export function handleSummary(data) {
    const summary = evidenceSummary(data);
    const text = [
        '',
        '=== PayFlow transfer test evidence ===',
        `runId: ${summary.runId}`,
        `mode: ${summary.mode}`,
        `iterations: ${summary.iterations}`,
        `business succeeded: ${summary.businessSucceeded}`,
        `business failed: ${summary.businessFailed}`,
        `compensation required: ${summary.compensationRequired}`,
        `invalid responses: ${summary.invalidResponses}`,
        `recovered after timeout: ${summary.recoveredAfterTimeout}`,
        `dropped iterations: ${summary.droppedIterations}`,
        `achieved business TPS: ${summary.achievedBusinessTps.toFixed(2)}`,
        `response avg/p95/p99: ${summary.responseTimeMs.avg.toFixed(2)} / ${summary.responseTimeMs.p95.toFixed(2)} / ${summary.responseTimeMs.p99.toFixed(2)} ms`,
        '========================================',
        '',
    ].join('\n');

    return {
        stdout: text,
        [`${resultDir}/k6-summary.json`]: JSON.stringify(summary, null, 2),
        [`${resultDir}/k6-raw-summary.json`]: JSON.stringify(data, null, 2),
        [`${resultDir}/k6-summary.txt`]: text,
    };
}
