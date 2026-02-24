1. 목표와 배경
   1.1 요구사항

선착순 쿠폰 이벤트에서 **정해진 수량(N개)**만 발급한다.

동일 사용자가 쿠폰을 중복 발급받지 못하게 막는다.

이벤트 시작 시점에 트래픽이 폭증할 수 있으므로 DB 경합을 줄이고 안정적으로 처리해야 한다.

Redis 기반으로 선착순 요청 큐잉 및 중복/수량 제어를 수행한다.

1.2 기존 방식의 문제(개선 필요성)

기존에 DB 분산락 기반으로 처리하면:

요청이 몰릴 때 DB 락 경합이 커지고 TPS가 급격히 떨어질 수 있다.

이벤트 트래픽이 전체 서비스 DB 성능에 영향을 줄 수 있다.

따라서 Redis를 “게이트”로 사용하여:

DB로 들어가는 요청을 이벤트 당첨자만으로 최소화

선착순/중복을 Redis에서 빠르게 판정

DB는 최종 발급 내역 저장 역할에 집중하게 한다.

2. 설계 개요

본 구현은 신청/확정 분리형으로 설계한다.

신청(Apply): 사용자의 요청을 Redis에 선착순 순서대로 적재(ZSET)

확정(Issue): 워커(배치)가 Redis 큐를 소비하여 실제 DB 발급을 수행

이 구조는 다음 장점이 있다.

요청 폭주 시 DB에 직접 부하가 걸리지 않는다.

워커 처리량을 조절하여 시스템 안정성을 확보한다.

장애/재시도 설계(멱등성, 재큐잉)가 가능하다.

3. Redis 키 설계

쿠폰 이벤트(쿠폰 단위) 별로 다음 키를 사용한다.

3.1 활성 이벤트 목록

coupon:active (SET)

현재 워커가 처리해야 하는 couponId 목록

이벤트 시작 시 등록 / 종료 시 제거

TTL: 이벤트 종료 시각 또는 2일

3.2 선착순 요청 큐

coupon:{couponId}:req (ZSET)

member: userId (문자열)

score: timestamp(ms) + tie-breaker

역할: 신청 순서(선착순)를 유지하는 대기열

3.3 남은 수량

coupon:{couponId}:remain (STRING)

value: 남은 발급 가능 수량

워커가 원자적으로 차감하여 수량 초과 발급을 방지한다.

3.4 워커 단계 중복 방지(선택)

coupon:{couponId}:issued (SET)

워커가 “이미 처리한 유저”를 기록하여 멱등성 강화 및 DB 중복 예외 감소

4. 타이브레이커 설계
4.1 필요성

동일한 밀리초(ms)에 여러 요청이 들어오면 score가 같아질 수 있으며, score 동일 시 정렬 기준이 불안정해질 수 있다.

4.2 방식(시간 + seq packing)

score = nowMs * SHIFT + (seq % SHIFT)

seq는 Redis INCR로 원자적으로 발급한다.

SHIFT는 1_000 등으로 설정하여 double 정밀도 문제를 줄인다.

효과:

같은 ms에 도착한 요청도 순서가 안정적으로 결정된다.

5. 기능 동작 흐름
5.1 신청(Apply) API 흐름

SADD applied/lock 등으로 중복 클릭을 억제(단기 TTL)

INCR seqKey로 tie-break seq 확보

ZADD reqKey userId score로 대기열 등록

응답 반환: ACCEPTED 또는 DUPLICATE

신청 단계에서는 DB를 호출하지 않는다.
즉, 이벤트 폭주 시에도 DB 경합을 최소화한다.

5.2 확정(Issue) 워커 흐름

워커는 주기적으로(예: 200ms) 실행되며, 활성 쿠폰 목록을 Redis에서 가져와 처리한다.

Step 0) 활성 couponId 목록 가져오기

SMEMBERS coupon:active → couponId 리스트 획득

Step 1) 배치 단위로 수량 선점

remain에서 처리할 개수(want)를 선점한다.

INCRBY remain -want (StringRedisTemplate에서는 increment(key, -want) 사용)

remain이 음수가 되면 oversold 만큼 되돌리고 실제 선점량(reserved)을 계산한다.

reserved <= 0이면 품절 처리.

Step 2) 선착순 요청 pop

ZPOPMIN reqKey reserved

실제 pop된 개수(actual)가 reserved보다 적으면, 부족분만큼 remain을 복구한다.

Step 3) pop된 요청 처리(유저 단위)

각 userId에 대해:

SADD issued userId

0이면 이미 처리된 유저 → remain 1 복구하고 continue

DB insert로 발급 확정

DB 결과에 따라 보상/재큐잉 처리

6. DB 저장 및 중복 발급 방지
   6.1 DB의 역할

Redis는 빠른 게이트 역할이며, 최종 발급 내역은 DB에 저장되어야 한다.

사용자 “쿠폰함” 조회

CS/정산/감사 로그

시스템 장애 복구 기준(진실의 원천)

6.2 유니크 제약

(coupon_id, user_id) 유니크(해당 쿠폰을 유저당 1회만 발급하는 정책일 때)

만약 couponId가 “템플릿”이고 반복 발급이 가능하다면,
향후 issuanceId(발급 회차/이벤트 단위)를 도입해 유니크를 (issuance_id, user_id)로 분리하는 방식이 확장에 유리하다.

7. 장애/실패 처리 전략
   7.1 DB insert 실패 분류(IssueResult)

DB 실패를 아래 3가지로 분류하여 무한 재시도를 방지한다.

SUCCESS: 정상 발급 완료

PERMANENT_FAIL: 유니크 충돌/제약 위반 등 재시도해도 실패

RETRYABLE_FAIL: 일시 장애(타임아웃/커넥션 등)로 재시도 가능

7.2 PERMANENT_FAIL 처리

issued 제거(넣었다면)

remain 복구(+1)

재큐잉 금지
→ 재큐잉하면 같은 유저가 계속 실패하며 무한 루프가 발생할 수 있음

7.3 RETRYABLE_FAIL 처리(재시도)

issued 제거

remain 복구(+1)

reqKey에 재큐잉(requeue)

원래 score 또는 현재 시각 기반 score 사용

backoff를 위해 score를 +5000ms 등으로 증가시켜 “너무 빨리 다시 잡히지 않도록” 함

(중요) 같은 실행에서 무한 반복을 막기 위해:

재큐잉 후 return하여 다음 스케줄 tick에서 재시도하도록 설계 가능

8. 운영 관점 고려사항
   8.1 워커가 계속 도는 문제

이벤트가 종료/품절인데도 coupon:active에 남아있으면 워커는 계속 돈다.

해결:

이벤트 종료 시 coupon:active에서 제거

또는 remain이 0이 되면 워커가 자동으로 제거(정책에 따라 선택)

8.2 DB 장애 시 폭격 방지

DB가 장시간 장애면 워커가 계속 insert를 시도할 수 있다.

대응:

retryableFail이 연속되면 coupon별 pauseKey를 둬서 일정 시간 처리 중단(서킷 브레이커)

또는 retry 횟수 제한 후 DLQ/격리

9. 기대 효과
   9.1 DB 부하 감소

신청 시점에 DB 접근이 없어 폭주 트래픽을 Redis가 흡수한다.

워커가 승자만 DB에 기록하며, 처리량을 조절할 수 있다.

9.2 정합성 및 안정성 확보

remain 선점으로 수량 초과 발급 방지

DB 유니크로 중복 발급 최종 차단

실패 타입 분리로 무한 재시도/무한 루프 방지

재큐잉/backoff로 일시 장애 대응 가능

10. 결론

본 설계는 Redis를 기반으로 선착순 요청을 안정적으로 큐잉하고, 워커를 통해 발급을 확정함으로써 폭주 트래픽 상황에서도 DB 경합을 줄이면서 “정해진 수량만 / 중복 없이” 쿠폰을 발급할 수 있다. 또한 실패 타입 분류 및 재큐잉 정책을 통해 장애 상황에서도 시스템이 과도하게 폭주하지 않도록 설계했다.