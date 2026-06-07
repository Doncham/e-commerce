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

// DB에 만들어둔 user 수.
// 1만 명 만들었으면 USER_COUNT=10000
const USER_COUNT = Number(__ENV.USER_COUNT || 10000);

export const options = {
    scenarios: {
        coupon_apply: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 1000),
            timeUnit: '1s',
            duration: __ENV.DURATION || '1m',
            preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 100),
            maxVUs: Number(__ENV.MAX_VUS || 300),
        },
    },
    thresholds: {
        // 429를 의도적으로 허용하는 실험이면 http_req_failed를 빼거나 완화해야 함
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

export default function () {
    /**
     * __ITER는 VU별 iteration이라 전체 유니크 보장이 약함.
     * 그래서 __VU와 __ITER를 조합해서 userId를 만든다.
     *
     * 단, USER_COUNT보다 총 요청 수가 많으면 userId가 다시 반복된다.
     * 예: USER_COUNT=10000인데 60000요청을 보내면 중복 신청이 발생함.
     */
    const rawUserId = (__VU * 1_000_000) + __ITER;
    const userId = (rawUserId % USER_COUNT) + 1;

    const url = `${BASE_URL}/api/v1/first-come-coupons/apply?userId=${userId}&couponId=${COUPON_ID}`;

    const res = http.post(url, null, {
        tags: {
            name: 'coupon_apply',
        },
    });

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    if (res.status === 429) {
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