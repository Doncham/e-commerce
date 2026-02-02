import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
    stages: [
        { duration: "30s", target: 300 },  // ramp-up
        { duration: "1m",  target: 300 },  // steady
        { duration: "30s", target: 0 },   // ramp-down
    ],
    thresholds: {
        http_req_failed: ["rate<0.01"],                 // 실패율 < 1%
        http_req_duration: ["p(95)<300", "p(99)<600"],  // 목표는 네가 조정
    },
};

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const RANGES = ["SEVEN", "THIRTY"];
const range = RANGES[Math.random() < 0.8 ? 0 : 1];

export default function () {
    const url = `${BASE_URL}/api/v1/product/popular?range=${range}`;

    const res = http.get(url, {
        headers: {
            "Accept": "application/json",
        },
    });

    check(res, {
        "status is 200": (r) => r.status === 200,
        "is json": (r) => (r.headers["Content-Type"] || "").includes("application/json"),
    });
}