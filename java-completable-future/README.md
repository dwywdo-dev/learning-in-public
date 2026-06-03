# Java CompletableFuture Side Project

이 프로젝트는 `CompletableFuture`를 바닥부터 다시 구현해보는 학습용 사이드 프로젝트다.

목표는 "사용자 프로필 화면 집계기"를 만들면서 다음을 직접 관찰하는 것이다.

- `supplyAsync`가 작업을 언제 제출하는지
- `thenApply`, `thenCompose`, `thenCombine`, `allOf`의 타입과 실행 thread 차이
- 완료 순서와 결과 순서가 왜 다를 수 있는지
- batch 처리와 sliding window 동시성 제한의 차이
- timeout이 future 상태만 바꾸고 underlying blocking work를 멈추지 않을 수 있다는 점
- retry/backoff/jitter가 왜 `Supplier<CompletableFuture<T>>`에서 출발해야 하는지
- bounded executor와 rejection policy가 backpressure를 어떻게 드러내는지

## 사이드 프로젝트 시나리오

사용자 프로필 화면은 네 종류의 데이터를 조합한다.

```text
ProfilePage
├── UserProfile      DB 조회, blocking
├── RecentOrders     DB 조회, blocking
├── Recommendations  외부 API 호출, 실패하면 빈 리스트 fallback
└── CouponCount      Redis 조회, 빠름
```

기본 요구사항:

- 서로 독립적인 조회는 병렬로 시작한다.
- blocking DB, external API, Redis 호출은 각각 어떤 executor에서 실행되는지 로그로 확인한다.
- 추천 상품 조회 실패는 전체 실패로 만들지 않고 fallback한다.
- 전체 화면 응답에는 deadline을 둔다.
- 대량 사용자 집계는 batch와 sliding window 두 방식으로 각각 구현한다.
- retry는 같은 future를 재사용하지 않고 새 attempt future를 만든다.

## 진행 방식

테스트는 처음에 `@Disabled`로 막혀 있다. 과제 하나를 진행할 때 해당 테스트의 `@Disabled`를 제거하고, 최소 구현으로 통과시킨다.

권장 순서:

1. `ProfileAggregatorExerciseTest`
2. `FutureUtilityExerciseTest`
3. `RetryExerciseTest`
4. `TimeoutObservationTest`
5. `BoundedExecutorObservationTest`

## 실행

```bash
./gradlew test
./gradlew run
```

특정 테스트만 실행:

```bash
./gradlew test --tests 'org.example.project.ProfileAggregatorExerciseTest'
```
