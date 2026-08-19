# Prevent Duplicate Payment Per User

## Problem

서로 다른 orderSessionId를 사용하면 동일 사용자가
Order #1, Order #2를 동시에 만들 수 있다.

각 Payment는 서로 다른 row이므로 Payment row lock만으로
동시 PG confirm을 막을 수 없다.

## Decision

READY -> CONFIRMING 전환 시 user 단위 동시성 제어를 수행한다.

한 사용자는 동시에 하나의 CONFIRMING Payment만 허용한다.

## Why

PG confirm 결과가 불확실한 상태에서 다른 Payment confirm을
허용하면 이중결제가 발생할 수 있다.

## Alternatives

- 사용자당 Order를 하나만 허용
- Redis distributed lock
- 별도 payment confirmation guard 테이블

## Current Choice

우선 User row pessimistic lock 사용.