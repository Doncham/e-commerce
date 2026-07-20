import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const accepted = new Counter('coupon_accepted');
const duplicate = new Counter('coupon_duplicate');
const soldOut = new Counter('coupon_sold_out');
const tooManyRequests = new Counter('coupon_too_many_requests');
const unknown = new Counter('coupon_unknown');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = Number(__ENV.COUPON_ID || 1);

export const options = {
    scenarios: {
        coupon_apply: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 100),
            timeUnit: '1s',
            duration: __ENV.DURATION || '30s',
            preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 1500),
            maxVUs: Number(__ENV.MAX_VUS || 3000),
        },
    },
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'max',
        'p(90)',
        'p(95)',
        'p(99)'
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<200', 'p(99)<500'],
    },
};

export default function () {
    const userId = (__VU * 1000000) + __ITER;

    const url = `${BASE_URL}/api/v1/first-come-coupons/apply?userId=${userId}&couponId=${COUPON_ID}`;

    const res = http.post(url, null, {
        tags: {
            name: 'coupon_apply',
        },
    });

    check(res, {
        'status is 200 or 429': function (r) {
            return r.status === 200 || r.status == 429;
        },
    });

    if(res.status === 429) {
        tooManyRequests.add(1);
        return;
    }
    if (res.status !== 200) {
        unknown.add(1);
        return;
    }

    let body;

    try {
        body = res.json();
    } catch (e) {
        unknown.add(1);
        return;
    }

    if (body.status === 'ACCEPTED') {
        accepted.add(1);
    } else if (body.status === 'DUPLICATE') {
        duplicate.add(1);
    } else if (body.status === 'SOLD_OUT') {
        soldOut.add(1);
    } else {
        unknown.add(1);
    }
}