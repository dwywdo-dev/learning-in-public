# Spring Batch 학습

## 1. Tasklet 기반 Job 실행 흐름

`HelloJobConfig`를 통해 확인한 Spring Batch 애플리케이션의 실행 흐름을 컴포넌트 단위로 정리한다.

### 컴포넌트 구성

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot Application                                        │
│                                                                  │
│  ┌──────────────────────┐    ┌───────────────────────────────┐  │
│  │  HikariCP            │    │  JobRepository                │  │
│  │  (DB Connection Pool) │───│  (메타데이터 저장소)            │  │
│  │                      │    │  - BATCH_JOB_INSTANCE         │  │
│  │  DataSource: H2 mem  │    │  - BATCH_JOB_EXECUTION        │  │
│  └──────────┬───────────┘    │  - BATCH_STEP_EXECUTION       │  │
│             │                └───────────────┬───────────────┘  │
│             │                                │                   │
│  ┌──────────┴────────────────────────────────┴───────────────┐  │
│  │  JobLauncherApplicationRunner                             │  │
│  │  (앱 기동 완료 후, 등록된 Job을 찾아 자동 실행)              │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                    │
│  ┌──────────────────────────┴────────────────────────────────┐  │
│  │  SimpleJobLauncher                                        │  │
│  │  (Job 실행 담당, 동기 실행 - TaskExecutor 미설정 시 기본값)   │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                    │
│  ┌──────────────────────────┴────────────────────────────────┐  │
│  │  Job: helloJob                                            │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  Step: helloStep                                    │  │  │
│  │  │                                                     │  │  │
│  │  │  ┌───────────────────────────────────────────────┐  │  │  │
│  │  │  │  Tasklet                                      │  │  │  │
│  │  │  │  println("Hello, Spring Batch!")               │  │  │  │
│  │  │  │  return RepeatStatus.FINISHED                  │  │  │  │
│  │  │  └───────────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 실행 흐름 (시간순)

```
[앱 기동]
  │
  ├── Spring Boot 시작 (Java 21)
  ├── HikariCP: H2 인메모리 DB 커넥션 풀 생성
  ├── JobRepository: DB 타입 자동 감지 (H2), 메타 테이블 초기화
  ├── SimpleJobLauncher: 동기 실행 모드로 준비
  │
  ▼
[Job 자동 실행]
  │
  ├── JobLauncherApplicationRunner: 등록된 Job 탐색
  ├── SimpleJobLauncher: helloJob 실행 (parameters: {})
  │     │
  │     └── SimpleStepHandler: helloStep 실행
  │           │
  │           └── Tasklet: "Hello, Spring Batch!" 출력
  │                 └── RepeatStatus.FINISHED 반환 → Step 종료
  │
  ├── helloStep: COMPLETED (4ms)
  ├── helloJob: COMPLETED (10ms) → 결과를 메타 테이블에 기록
  │
  ▼
[종료]
  │
  └── HikariCP: 커넥션 풀 종료 → 프로세스 종료
```

### 로그와 컴포넌트 매핑

| 로그 출처 (약어) | 실제 컴포넌트 | 역할 |
|---|---|---|
| `c.e.batch.BatchApplicationKt` | BatchApplication | 앱 진입점 |
| `c.z.hikari.HikariDataSource` | HikariCP | DB 커넥션 풀 관리 |
| `o.s.b.c.r.s.JobRepositoryFactoryBean` | JobRepository | 실행 이력 저장소 초기화 |
| `o.s.b.c.l.support.SimpleJobLauncher` | SimpleJobLauncher | Job 실행 및 완료 보고 |
| `o.s.b.a.b.JobLauncherApplicationRunner` | JobLauncherApplicationRunner | 기동 후 Job 자동 실행 트리거 |
| `o.s.batch.core.job.SimpleStepHandler` | SimpleStepHandler | Step 실행 위임 |
| `o.s.batch.core.step.AbstractStep` | AbstractStep | Step 실행 시간 측정 및 상태 관리 |

### DataSource 분리: 메타데이터 vs 비즈니스 데이터

Spring Batch는 Job 실행 이력을 메타 테이블에 기록한다. 이 메타 테이블과 실제 비즈니스 데이터 테이블은 보통 다른 DB에 둔다.

**현재 (학습용) - 단일 DataSource:**

```
H2 인메모리 DB (하나)
├── BATCH_JOB_INSTANCE      ← 메타데이터
├── BATCH_JOB_EXECUTION     ← 메타데이터
├── BATCH_STEP_EXECUTION    ← 메타데이터
└── accounts                ← 비즈니스 데이터
```

**실무 - DataSource 분리:**

```
DB 1: 메타데이터 전용              DB 2: 비즈니스 데이터 (기존 서비스 DB)
├── BATCH_JOB_INSTANCE             └── accounts
├── BATCH_JOB_EXECUTION
└── BATCH_STEP_EXECUTION
```

분리하는 이유:
- 메타데이터 쓰기가 비즈니스 DB에 부하를 주지 않도록
- 비즈니스 DB와 배치 인프라의 생명주기가 다름
- 비즈니스 DB에 Spring Batch 전용 테이블이 섞이는 것을 방지

분리 방법: DataSource를 2개 등록하고 `@BatchDataSource`로 메타데이터용을 지정한다.

```kotlin
@Bean
@BatchDataSource  // 이 DataSource를 메타데이터용으로 사용
fun batchDataSource(): DataSource { ... }

@Bean
@Primary          // 나머지(비즈니스 로직)는 이쪽을 사용
fun businessDataSource(): DataSource { ... }
```

### 메타 테이블은 필수인가?

**필수이다.** Spring Batch는 메타 테이블 없이 동작하지 않는다. `JobRepository`가 Job 실행 상태를 메타 테이블에 기록하고 읽는 것이 핵심 설계이다.

메타 테이블 저장소 옵션:

| 방식 | 특징 |
|---|---|
| 실제 DB (MySQL, PostgreSQL 등) | 실무 표준. 이력이 영구 보존 |
| H2 인메모리 (학습용) | 앱 종료 시 데이터 소멸 |
| Map 기반 인메모리 (`MapJobRepositoryFactoryBean`) | 메타 테이블 없이 메모리에만 저장. 테스트 전용 |

### H2 인메모리 사용 시 동작하는 기능 / 안 하는 기능

핵심 구분은 **"실행 중"인지 "실행 간"인지**이다.

**실행 중 (H2에서도 정상 동작):**

| 기능 | 이유 |
|---|---|
| Retry/Skip | chunk 처리 중 메모리 + 현재 트랜잭션 내에서 동작 |
| Chunk 트랜잭션 관리 | 현재 실행의 DB 트랜잭션 |
| Step 실행 순서 제어 | 현재 Job 실행 컨텍스트 내에서 결정 |
| Listener 호출 | 현재 실행의 이벤트 |
| READ_COUNT, WRITE_COUNT 등 집계 | 현재 실행의 STEP_EXECUTION에 기록 |

**실행 간 (H2 인메모리에서는 동작하지 않음):**

| 기능 | 이유 |
|---|---|
| 같은 Parameter 중복 실행 방지 | 이전 실행 이력이 앱 종료 시 소멸 |
| 실패 지점부터 재시작 | 이전 실행에서 어디까지 처리했는지 기록이 없음 |
| 실행 이력 조회 | 앱 종료 시 모두 사라짐 |

```
실패 지점 재시작 예시:

영속 DB (MySQL 등):
  1차 실행: Chunk #1~#47 성공, #48 실패 → FAILED 기록
  2차 실행: 메타 테이블 확인 → Chunk #48부터 재시작

H2 인메모리:
  1차 실행: Chunk #1~#47 성공, #48 실패 → FAILED 기록 → 앱 종료 → 소멸
  2차 실행: 이전 기록 없음 → Chunk #1부터 다시 시작
```

H2 인메모리에서 안 되는 것은 "이전 실행을 기억해야 하는 기능" 뿐이다. 학습 단계에서는 문제없고, 실무에서 영속 DB로 전환하면 자동으로 활성화된다.

## 2. Chunk 기반 Job

Tasklet이 "하나의 작업을 통째로 실행"하는 방식이라면, Chunk는 "데이터를 일정 단위로 나눠서 파이프라인으로 처리"하는 방식이다.

### Reader → Processor → Writer 파이프라인

```
AccountJobConfig

Job: accountJob
 └── Step: accountStep (chunk-size = 5)
      │
      │  ┌─────────────────────────────────────────────────────────┐
      │  │  JdbcCursorItemReader                                    │
      │  │  DB에서 accounts 테이블의 전체 행을 읽는다                 │
      │  │  .sql("SELECT ... FROM accounts ORDER BY account_id")   │
      │  │  .rowMapper { rs -> Account(...) }                      │
      │  └────────────────────────┬────────────────────────────────┘
      │                           ↓
      │  ┌────────────────────────┴────────────────────────────────┐
      │  │  ItemProcessor<Account, Account>                        │
      │  │  조건 평가: status == "ACTIVE" && balance >= 100,000     │
      │  │  부합 → Account 반환 (Writer로 전달)                     │
      │  │  불일치 → null 반환 (필터링됨)                            │
      │  └────────────────────────┬────────────────────────────────┘
      │                           ↓
      │  ┌────────────────────────┴────────────────────────────────┐
      │  │  ItemWriter<Account>                                    │
      │  │  chunk 단위로 모아서 한 번에 받음                         │
      │  │  각 Account에 대해 외부 API 호출 (현재는 println Mock)    │
      │  └─────────────────────────────────────────────────────────┘
```

### Chunk 동작 방식

chunk-size=5는 **Reader가 5건을 읽을 때마다** Processor → Writer 파이프라인이 한 번 실행된다는 의미이다.
Processor에서 null을 반환한 건은 필터링되므로, Writer가 실제로 받는 건수는 chunk-size보다 적을 수 있다.

```
데이터 20건, chunk-size=5 실행 결과:

Chunk #1: Reader 5건 읽음 → Processor 통과 3건 → Writer 3건 처리
  Alice(150,000), Bob(320,000), Diana(780,000)
  필터링: Charlie(INACTIVE), Eve(balance 12,000)

Chunk #2: Reader 5건 읽음 → Processor 통과 1건 → Writer 1건 처리
  Hank(1,200,000)
  필터링: Frank(DORMANT), Grace(balance 95,000), Iris(INACTIVE), Jack(balance 5,000)

Chunk #3: Reader 5건 읽음 → Processor 통과 2건 → Writer 2건 처리
  Karen(540,000), Mia(310,000)
  필터링: Leo(balance 45,000), Nancy(INACTIVE), Oscar(balance 78,000)

Chunk #4: Reader 5건 읽음 → Processor 통과 3건 → Writer 3건 처리
  Paul(890,000), Quinn(130,000), Tina(410,000)
  필터링: Rachel(balance 67,000), Sam(DORMANT), Uma(balance 88,000)

총 4개 chunk, 9건 API 호출, 11건 필터링
```

### Reader 종류에 따른 데이터 조회 방식

코드만 보면 "Reader가 전체 데이터를 먼저 조회한 뒤 chunk 단위로 쪼개는 것"처럼 보이지만, 실제 동작은 Reader 종류에 따라 다르다.

**DB 커서(Cursor)란?**

쿼리 결과셋을 가리키는 포인터이다. 결과를 한 번에 받는 것이 아니라, 포인터를 이동시키며 1건씩 꺼낸다.

```
일반 쿼리 (커서 없음):
  앱 → SELECT * FROM accounts → DB
  앱 ← 10,000건 전부 한꺼번에 받음
  → 메모리에 10,000건이 한 번에 올라옴

커서 방식:
  앱 → SELECT * FROM accounts (커서 오픈) → DB가 결과셋 준비 (아직 전송 안 함)
  앱 → "다음 1건 줘" (fetch) ← DB: 1건 반환
  앱 → "다음 1건 줘" (fetch) ← DB: 1건 반환
  ...
  앱 → "커서 닫기" (close)   → DB: 결과셋 해제
```

비유: 일반 쿼리는 도서관에서 책 10,000권을 한 번에 빌려오는 것이고,
커서는 도서관에 가서 책을 한 권씩 읽고 다음 책을 꺼내는 것이다.
단, 다 읽을 때까지 그 자리(커넥션)를 계속 차지하고 있어야 한다.

**Cursor 방식 (현재 사용 중: `JdbcCursorItemReader`)**

```
Step 시작
  │
  ├── 쿼리 1번 실행 → DB 커서 오픈
  │
  ├── Chunk #1
  │     read() → 커서에서 1건 fetch
  │     read() → 커서에서 1건 fetch
  │     ... (chunk-size만큼)
  │     → Processor → Writer → COMMIT
  │
  ├── Chunk #2
  │     read() → 커서에서 이어서 1건 fetch   ← 같은 커서에서 계속 읽음
  │     ...
  │     → Processor → Writer → COMMIT
  │
  └── 커서에서 더 이상 데이터 없음 → null → Step 종료
```

- 쿼리는 최초 1번만 실행되고, 커서가 열린 상태에서 1건씩 꺼냄
- 20건을 메모리에 한 번에 올리는 것이 아님
- **단점: 처리 시간이 길어지면 DB 커넥션을 오래 점유**

**Paging 방식 (`JdbcPagingItemReader` / `MyBatisPagingItemReader`)**

```
Step 시작
  │
  ├── Chunk #1
  │     쿼리 실행: SELECT ... LIMIT 5 OFFSET 0   ← 쿼리 1번
  │     read() × 5 (내부 버퍼에서 꺼냄)
  │     → Processor → Writer → COMMIT
  │
  ├── Chunk #2
  │     쿼리 실행: SELECT ... LIMIT 5 OFFSET 5   ← 쿼리 또 1번
  │     read() × 5
  │     → Processor → Writer → COMMIT
  │
  └── 쿼리 결과 0건 → null → Step 종료
```

- chunk마다 별도 쿼리를 실행하므로 **chunk 사이에 DB 커넥션을 점유하지 않음**
- 데이터가 매우 많은 실무 환경에서는 Paging 방식 권장

**비교 요약:**

|  | Cursor | Paging |
|---|---|---|
| DB 쿼리 횟수 | 1번 | chunk 수만큼 |
| 커넥션 점유 | Step 전체 동안 | chunk 처리 시에만 |
| 실무 권장 | 소량 데이터 | 대량 데이터 |

### chunk-size에 따른 차이

chunk-size는 Reader가 한 번에 몇 건을 읽어서 하나의 트랜잭션으로 묶을지를 결정한다.

| chunk-size | chunk 횟수 | 트랜잭션 수 | 특징 |
|---|---|---|---|
| 작게 (예: 5) | 많음 | 많음 | 실패 시 롤백 범위 작음, 커밋 빈번 |
| 크게 (예: 100) | 적음 | 적음 | 실패 시 롤백 범위 큼, 커밋 횟수 줄어듦 |

실무에서는 보통 100~1000 정도를 사용하며, DB 부하와 트랜잭션 크기를 고려해서 조절한다.

### 주의: ItemProcessor의 import

`ItemProcessor`가 두 개 존재한다. Spring Batch 것을 사용해야 한다.

```
org.springframework.batch.item.ItemProcessor  ← 사용해야 하는 것
javax.batch.api.chunk.ItemProcessor            ← Jakarta Batch API (다른 것)
```

## 3. Skip/Retry 정책

Chunk 처리 중 예외가 발생했을 때의 대응 전략이다.

### 기본 동작 (정책 없음)

```
Chunk 처리 중 예외 발생 → 해당 chunk 롤백 → Step 실패 → Job 실패
```

1건의 오류 때문에 전체 Job이 중단된다. 외부 API 호출처럼 일시적 실패가 가능한 경우 문제가 된다.

### Retry, Skip, 그리고 조합

```
Retry만:
  실패 → 재시도 1 → 재시도 2 → 재시도 3 → 그래도 실패하면 Step 실패

Skip만:
  실패 → 해당 건 스킵 → 다음 건 진행

Retry + Skip (실무 권장):
  실패 → 재시도 3번 → 그래도 실패 → 스킵하고 다음 건 진행
```

### 설정 방법

Step 빌더에 `.faultTolerant()`를 추가하여 활성화한다.

```kotlin
stepBuilderFactory.get("accountStep")
    .chunk<Account, Account>(5)
    .reader(accountReader())
    .processor(accountProcessor())
    .writer(accountWriter())
    .faultTolerant()                          // Skip/Retry 기능 활성화
    .retry(ApiCallException::class.java)      // 이 예외 발생 시 재시도
    .retryLimit(3)                            // 아이템 1건당 최대 3번 재시도
    .skip(ApiCallException::class.java)       // 재시도 후에도 실패하면 스킵
    .skipLimit(5)                             // Step 전체에서 최대 5건까지 스킵 허용
    .build()
```

### retryLimit과 skipLimit의 차이

이 둘은 서로 다른 단위에서 동작한다.

| 설정 | 단위 | 의미 |
|---|---|---|
| `retryLimit(3)` | 아이템 1건 기준 | 이 건을 몇 번 재시도할지 |
| `skipLimit(5)` | Step 전체 기준 | 총 몇 건까지 스킵을 허용할지 |

```
예: 20건 처리 중 6건이 계속 실패하는 경우 (retryLimit=3, skipLimit=5)

아이템 A 실패 → 재시도 3번 → 실패 → 스킵 (skipCount: 1/5)
아이템 B 실패 → 재시도 3번 → 실패 → 스킵 (skipCount: 2/5)
아이템 C 실패 → 재시도 3번 → 실패 → 스킵 (skipCount: 3/5)
아이템 D 실패 → 재시도 3번 → 실패 → 스킵 (skipCount: 4/5)
아이템 E 실패 → 재시도 3번 → 실패 → 스킵 (skipCount: 5/5)
아이템 F 실패 → 재시도 3번 → 실패 → skipLimit 초과 → Step 실패
```

### 실행 결과 (accountId=8 Hank가 항상 실패하는 시뮬레이션)

```
Chunk 1: Alice, Bob, Diana → 정상
Chunk 2: Hank → 실패 (1차 시도)         ← Writer 호출됨
Chunk 3: Hank → 실패 (재시도 1)         ← Writer 다시 호출됨
Chunk 4: Hank → 실패 (재시도 2)         ← Writer 다시 호출됨
Chunk 5: Hank → 실패 (재시도 3 = retryLimit 도달) → 스킵 (skipCount: 1/5)
Chunk 6: Karen, Mia → 정상
Chunk 7: Paul, Quinn, Tina → 정상

결과: Job COMPLETED (Hank 1건 스킵, 나머지 8건 정상 처리)
```

주의: Writer 내부의 chunkCount 변수는 재시도 시에도 증가한다. 실제 chunk는 4개이지만 재시도로 인해 Writer가 7번 호출된 것이다.

### 같은 chunk에 정상 아이템과 실패 아이템이 섞여있을 때

retry 단계에서는 chunk 단위로 통째 재시도된다. retryLimit 소진 후에는 scan 모드(1건씩 개별 처리)로 전환되어 실패한 건만 스킵한다.

```
retry 단계: chunk 통째 재시도
  Writer([Karen, Hank]) → Hank 실패 → chunk 롤백
  Writer([Karen, Hank]) → Hank 실패 → chunk 롤백
  Writer([Karen, Hank]) → Hank 실패 → retryLimit 도달

scan 모드: 1건씩 개별 처리하여 문제 아이템만 스킵
  Writer([Karen]) → 성공
  Writer([Hank])  → 실패 → 스킵
```

### 주의: "chunk 롤백"은 DB 트랜잭션 롤백이다

Spring Batch의 "chunk 롤백"은 DB 트랜잭션의 롤백이다. **외부 API 호출 같은 사이드 이펙트는 롤백되지 않는다.**

```
Writer([Karen, Hank])
  Karen API 호출 → 성공 (이미 호출됨, 되돌릴 수 없음)
  Hank API 호출 → 실패 → 예외
  → DB 트랜잭션 롤백
  → 재시도

Writer([Karen, Hank])  ← 재시도
  Karen API 호출 → 또 호출됨!  ← 중복 호출 발생
  Hank API 호출 → 또 실패
```

외부 API 호출이 포함된 Writer에서는 반드시 이 문제를 고려해야 한다.

**대응 방법:**

| 상황 | 대응 |
|---|---|
| API가 멱등(idempotent)하다 | 중복 호출 허용, 별도 처리 불필요 |
| API가 멱등하지 않다 | 처리 상태를 기록하고 재시도 시 건너뜀 |
| 중복 호출이 치명적이다 | chunk-size=1로 설정하거나 Retry 없이 Skip만 사용 |

멱등성 확보 예시 - 요청에 고유 ID를 포함하여 서버가 중복을 감지하도록 한다:

```
POST /api/accounts/notify
{ "accountId": 11, "requestId": "batch-20260313-11" }
→ 서버가 requestId 기준으로 이미 처리했으면 무시
```

### Retry + Skip 조합이 Skip 단독보다 효율적인 이유

Skip만 사용하면 실패 즉시 scan 모드(1건씩 개별 처리)로 진입한다. Retry를 먼저 두면 chunk 단위로 재시도하므로, 일시적 오류 시 훨씬 효율적이다.

```
일시적 네트워크 오류 (2초 뒤 복구) 상황 비교:

[Retry + Skip]
  Writer([Karen, Hank, Mia]) → 네트워크 오류 → 실패
    (2초 후 네트워크 복구)
  Writer([Karen, Hank, Mia]) → 재시도 → 3건 모두 성공

  Writer 호출: 2번
  결과: 3건 모두 정상 처리

[Skip만]
  Writer([Karen, Hank, Mia]) → 네트워크 오류 → 실패
  → 바로 scan 모드 진입 (1건씩 개별 처리)
  Writer([Karen]) → 네트워크 아직 불안정 → 실패 → 스킵
  Writer([Hank])  → 복구됨 → 성공
  Writer([Mia])   → 성공

  Writer 호출: 4번
  결과: Karen은 정상 데이터인데 스킵됨
```

외부 API 호출에서는 타임아웃, 순간 장애 등 일시적 오류가 흔하다. Retry로 chunk 단위 재시도를 먼저 시도하고, 그래도 안 되면 Skip으로 넘어가는 조합이 실무적으로 합리적이다.

### 병렬 처리의 3가지 레벨

Spring Batch의 병렬 처리는 레벨에 따라 병렬화 대상이 다르다.

```
레벨 1: Writer 내부 병렬화
  → 하나의 chunk 안에서 아이템을 동시에 처리 (예: API 동시 호출)
  → 개발자가 직접 구현 (parallelStream, 코루틴 등)

레벨 2: Multi-threaded Step
  → 여러 chunk를 동시에 처리
  → Step에 TaskExecutor를 설정하면 Spring Batch가 제공

레벨 3: Partitioning
  → 데이터를 파티션으로 나눠서 여러 Step 인스턴스가 병렬 처리
  → 대량 데이터에 적합, Spring Batch가 제공
```

**레벨 1과 레벨 2의 차이:**

| Writer 작업 특성 | Writer 내부 병렬화 (레벨 1) | Multi-threaded Step (레벨 2) |
|---|---|---|
| 외부 API 호출 (건당 느림) | 효과 큼 | chunk 간 병렬은 되지만 chunk 내부는 여전히 느림 |
| DB 배치 INSERT (한 번에 처리) | 불필요 | 효과 큼 |

### Multi-threaded Step과 Reader의 thread-safety

Multi-threaded Step은 Reader 1개를 여러 스레드가 공유한다. 여러 스레드가 동시에 `read()`를 호출하므로 Reader가 thread-safe해야 한다.

```
싱글 스레드:
  Thread-1: read()×5 → Process → Write  (Chunk #1)
  Thread-1: read()×5 → Process → Write  (Chunk #2)

Multi-threaded (3 스레드):
          ┌── Reader (1개, 공유) ──┐
          │                        │
    ┌─────┼─────┐                  │
    ↓     ↓     ↓                  │
  Th-1  Th-2  Th-3                 │
  read  read  read  ← 동시 호출    │
  ×5    ×5    ×5                   │
    ↓     ↓     ↓                  │
  Proc  Proc  Proc                 │
    ↓     ↓     ↓                  │
  Write Write Write                │
          └────────────────────────┘
```

thread-safe하지 않은 Reader를 사용하면 같은 데이터를 중복으로 읽을 수 있다:

```
Thread-1: "OFFSET 0 LIMIT 5"
Thread-2: "OFFSET 0 LIMIT 5"  ← Thread-1이 안 끝남, 같은 페이지 중복 요청
```

Paging Reader는 `read()`에 `synchronized`가 걸려있어 이 문제가 없다:

```
Thread-1: read() lock 획득 → OFFSET 0 LIMIT 5 → lock 해제
Thread-2: read() lock 획득 → OFFSET 5 LIMIT 5 → lock 해제
→ 페이지가 겹치지 않음
```

**Reader별 Multi-threaded Step 호환성:**

| Reader | Thread-safe | Multi-threaded Step |
|---|---|---|
| `JdbcPagingItemReader` | O | 가능 |
| `MyBatisPagingItemReader` | O | 가능 |
| `JdbcCursorItemReader` | X | 불가 |
| `MyBatisCursorItemReader` | X | 불가 |

대량 데이터에서 Paging 방식이 권장되는 이유: 커넥션 점유 문제 + Multi-threaded Step 호환.

## 4. MyBatisPagingItemReader 전환

`JdbcCursorItemReader`에서 `MyBatisPagingItemReader`로 전환했다.

### 전환 이유

| | JdbcCursorItemReader (이전) | MyBatisPagingItemReader (전환 후) |
|---|---|---|
| thread-safety | X → Multi-threaded Step 불가 | O → Multi-threaded Step 가능 |
| 커넥션 점유 | Step 전체 동안 | chunk 사이에 반환 |
| SQL 관리 | Kotlin 코드에 직접 작성 | MyBatis Mapper XML로 분리 |
| 기존 Mapper 활용 | 불가 | 가능 |

### 구성 파일

**1. Mapper 인터페이스** (`mapper/AccountMapper.kt`)

```kotlin
@Mapper
interface AccountMapper {
    fun findTargetAccounts(params: Map<String, Any>): List<Account>
}
```

**2. Mapper XML** (`resources/mapper/AccountMapper.xml`)

```xml
<mapper namespace="com.example.batch.mapper.AccountMapper">
    <select id="findTargetAccounts" resultType="com.example.batch.domain.Account">
        SELECT account_id, name, status, balance, created_at
        FROM accounts
        ORDER BY account_id
        LIMIT #{_pagesize} OFFSET #{_skiprows}
    </select>
</mapper>
```

- `#{_skiprows}`, `#{_pagesize}`는 Spring Batch가 자동으로 주입하는 페이징 파라미터
- WHERE 절, JOIN 등 자유롭게 추가 가능

**3. Reader Bean** (`AccountJobConfig.kt`)

```kotlin
@Bean
fun accountReader(sqlSessionFactory: SqlSessionFactory): MyBatisPagingItemReader<Account> =
    MyBatisPagingItemReaderBuilder<Account>()
        .sqlSessionFactory(sqlSessionFactory)
        .queryId("com.example.batch.mapper.AccountMapper.findTargetAccounts")
        .pageSize(5)
        .build()
```

- `sqlSessionFactory`는 `mybatis-spring-boot-starter`가 자동 등록한 Bean
- `.queryId()`는 Mapper XML의 namespace + select id
- `.pageSize()`는 chunk-size와 동일하게 설정하는 것이 일반적

**4. application.yml MyBatis 설정**

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true    # account_id → accountId 자동 매핑
```

### 내부 동작 차이

결과는 동일하지만 DB 접근 방식이 다르다:

```
이전 (Cursor): SELECT ... FROM accounts (커서 1번 열고 계속 fetch)

지금 (Paging): SELECT ... LIMIT 5 OFFSET 0   → Chunk #1
               SELECT ... LIMIT 5 OFFSET 5   → Chunk #2
               SELECT ... LIMIT 5 OFFSET 10  → Chunk #3
               SELECT ... LIMIT 5 OFFSET 15  → Chunk #4
```

## 5. Job Parameter

실행 시점에 외부에서 조건값을 전달하는 기능이다.

### 왜 필요한가

Processor의 조건이 코드에 하드코딩되어 있으면 조건을 바꿀 때마다 코드를 수정하고 재빌드해야 한다.

```bash
# 오늘은 잔액 100,000 이상
java -jar batch.jar minBalance=100000

# 내일은 잔액 500,000 이상
java -jar batch.jar minBalance=500000
```

### 중복 실행 방지

Spring Batch는 같은 Job + 같은 Parameter 조합은 중복 실행을 거부한다. 메타 테이블에 이미 COMPLETED로 기록되어 있기 때문이다.

```
java -jar batch.jar targetDate=2026-03-13  → 첫 실행 → COMPLETED
java -jar batch.jar targetDate=2026-03-13  → 거부 (이미 COMPLETED)
java -jar batch.jar targetDate=2026-03-14  → 새로운 실행 → OK
```

같은 파라미터로 재실행이 필요하면 `RunIdIncrementer`를 사용한다:

```kotlin
jobBuilderFactory.get("accountJob")
    .incrementer(RunIdIncrementer())  // 실행할 때마다 run.id가 자동 증가
    .start(accountStep())
    .build()
```

### 설정 방법

**1. `@StepScope` + `@Value`로 파라미터 주입**

```kotlin
@Bean
@StepScope
fun accountProcessor(
    @Value("#{jobParameters['minBalance']}") minBalance: Long,
): ItemProcessor<Account, Account> =
    ItemProcessor { account ->
        if (account.status == "ACTIVE" && account.balance >= minBalance) {
            account
        } else {
            null
        }
    }
```

- `@StepScope`: Bean 생성 시점을 Step 실행 시로 지연. 이래야 `jobParameters`를 읽을 수 있음
- `#{jobParameters['minBalance']}`: SpEL 표현식으로 Job Parameter에서 값을 꺼냄

**`@StepScope`가 왜 필요한가 - Bean 생성 시점 문제**

```
@StepScope 없을 때 (일반 Bean):

  앱 기동 시:
    Spring 컨텍스트 초기화
      ├── accountReader Bean 생성
      ├── accountProcessor Bean 생성  ← 이 시점에 @Value 해석
      ├── accountWriter Bean 생성     ← 그런데 jobParameters가 아직 없음!
      ├── accountStep Bean 생성
      └── accountJob Bean 생성
    앱 기동 완료
      ↓
    JobLauncher가 Job 실행 (minBalance=500000)  ← 여기서야 jobParameters 결정

  → Bean 생성 시점에 jobParameters가 없으므로 @Value 해석 실패
```

```
@StepScope 있을 때:

  앱 기동 시:
    Spring 컨텍스트 초기화
      ├── accountReader Bean 생성
      ├── accountProcessor → @StepScope 발견 → 프록시(껍데기)만 생성
      ├── accountWriter Bean 생성
      ├── accountStep Bean 생성 (processor 자리에 프록시가 들어감)
      └── accountJob Bean 생성
    앱 기동 완료
      ↓
    JobLauncher가 Job 실행 (minBalance=500000)
      ↓
    accountStep 실행 시작
      ↓
    프록시가 진짜 accountProcessor Bean 생성  ← 이제 jobParameters가 있음!
      @Value("#{jobParameters['minBalance']}") → 500000 주입 성공
```

프록시는 `ItemProcessor` 인터페이스를 구현하고 있어서 Step 입장에서는 진짜 객체인지 프록시인지 구분할 필요가 없다. 실제 `process()` 호출 시 프록시가 내부의 진짜 객체로 위임한다.

**2. `@StepScope` Bean을 Step에서 주입받기**

`@StepScope` Bean은 생성 시 jobParameters가 필요하므로, 직접 메서드 호출 대신 Bean 주입 방식을 사용해야 한다.

```kotlin
// 컴파일 에러 - accountProcessor()에 minBalance 인자가 필요
.processor(accountProcessor())

// 정상 - Spring이 @StepScope Bean을 알아서 주입
fun accountStep(
    sqlSessionFactory: SqlSessionFactory,
    accountProcessor: ItemProcessor<Account, Account>,
): Step =
    stepBuilderFactory.get("accountStep")
        ...
        .processor(accountProcessor)
        ...
```

**3. 실행 시 파라미터 전달**

```bash
./gradlew bootRun --args='minBalance=500000'
```

### 실행 결과 (minBalance=500000)

```
이전 (minBalance=100,000 하드코딩): 9건 통과
지금 (minBalance=500,000 파라미터): 3건 통과 (+ Hank 1건 스킵)

통과한 계정 (balance >= 500,000):
  Diana    780,000
  Hank   1,200,000  → API 실패 시뮬레이션으로 스킵
  Karen    540,000
  Paul     890,000
```

## 6. 다중 Step 구성

하나의 Job에 여러 Step을 조합하여 실행한다.

### 순차 실행

`.start()`와 `.next()`로 Step을 순서대로 연결한다.

```kotlin
jobBuilderFactory.get("accountJob")
    .start(initStep)          // 1번째
    .next(accountStep)        // 2번째
    .next(reportStep)         // 3번째
    .build()
```

```
실행 흐름:
  initStep (Tasklet) → accountStep (Chunk) → reportStep (Tasklet)
```

### 조건부 실행

Step의 ExitStatus에 따라 다음 Step을 분기한다.

```kotlin
jobBuilderFactory.get("accountJob")
    .start(initStep)
    .next(accountStep)
    .on("COMPLETED").to(reportStep)     // accountStep 성공 → reportStep
    .from(accountStep)
    .on("FAILED").to(errorStep)         // accountStep 실패 → errorStep
    .end()
    .build()
```

- `.on("상태")` → ExitStatus가 이 값일 때
- `.to(스텝)` → 이 Step으로 이동
- `.from(스텝)` → 같은 Step에서 다른 분기 조건 추가
- `.end()` → 흐름 정의 종료

```
실행 흐름:
  initStep → accountStep ─── COMPLETED → reportStep
                          └── FAILED    → errorStep
```

### Step이 FAILED가 되는 조건

Skip/Retry가 설정된 chunk Step에서는, **skipLimit을 초과해야 Step이 FAILED**가 된다. skipLimit 안에서 처리된 실패는 Step 결과에 영향을 주지 않는다.

```
skipLimit=5, 실패 1건 발생:
  → 스킵 처리 (skipCount: 1/5)
  → Step: COMPLETED        ← 실패가 있었지만 skipLimit 이내이므로 성공

skipLimit=0, 실패 1건 발생:
  → skipLimit 초과 (SkipLimitExceededException)
  → Step: FAILED           ← 조건부 실행에서 errorStep으로 분기
```

즉 faultTolerant + skip이 설정되어 있으면, 일정 수준의 실패는 허용되고 Job은 정상 완료된다. skipLimit을 넘어서는 실패가 발생해야 비로소 Step이 FAILED가 되어 에러 분기로 이동한다.

## 7. Listener

Job/Step/Chunk 실행 전후에 로직을 끼워넣는 기능이다.

### Listener 종류

```
Job 레벨:
  JobExecutionListener        → Job 시작 전 / 완료 후

Step 레벨:
  StepExecutionListener       → Step 시작 전 / 완료 후
  ChunkListener               → Chunk 시작 전 / 완료 후 / 에러 시

아이템 레벨:
  ItemReadListener            → read 전 / 후 / 에러
  ItemProcessListener         → process 전 / 후 / 에러
  ItemWriteListener           → write 전 / 후 / 에러

특수:
  SkipListener                → skip 발생 시 (read/process/write 각각)
```

### Listener 등록 위치와 빌더 체인

Step 빌더 체인에서 각 메서드는 **다른 타입의 빌더를 반환**한다. `.listener()` 메서드 이름은 같지만, 빌더 타입에 따라 받을 수 있는 Listener 타입이 다르다.

```
stepBuilderFactory.get("accountStep")       → StepBuilder
    .chunk<Account, Account>(5)             → SimpleStepBuilder
    .reader(...)
    .processor(...)
    .writer(...)
    .faultTolerant()                        → FaultTolerantStepBuilder
    .retry(...)
    .skip(...)
    .listener(SkipLoggingListener())        ← 여기서야 SkipListener 등록 가능
    .build()
```

각 빌더의 `.listener()`가 받는 타입:

| 빌더 | `.listener()`가 받는 타입 |
|---|---|
| `StepBuilder` | `StepExecutionListener` |
| `SimpleStepBuilder` | `StepExecutionListener`, `ChunkListener`, `ItemReadListener` 등 |
| `FaultTolerantStepBuilder` | 위의 전부 + `SkipListener`, `RetryListener` |

`SkipListener`는 skip 기능이 활성화(`faultTolerant()`)되어야 의미가 있으므로, `FaultTolerantStepBuilder`에서만 등록할 수 있다.

**잘못된 위치에 넣으면:**

```kotlin
// .chunk() 앞에 넣은 경우 - StepBuilder.listener() 호출
.listener(SkipLoggingListener())   // StepExecutionListener로 취급됨
.chunk<Account, Account>(5)

// 컴파일은 되지만 SkipListener로 인식되지 않아 skip 이벤트가 전달되지 않음
```

컴파일 에러가 나지 않아서 찾기 어려운 문제이다. `.listener()` 메서드 이름이 동일하기 때문.

### Listener 등록 위치 요약

| Listener | 등록 위치 |
|---|---|
| `JobExecutionListener` | `jobBuilderFactory.get().listener(...)` |
| `StepExecutionListener` | `.chunk()` 앞의 `.listener(...)` |
| `SkipListener` | `.faultTolerant()` 이후의 `.listener(...)` |

## 8. 남은 학습 주제

### 실무 적용 전 추가 학습

- [ ] **ExecutionContext** - Step 간 데이터 전달 (예: accountStep 처리 건수를 reportStep에서 참조)
- [ ] **JobParameter 검증** - 필수 파라미터 누락 시 Job 시작 자체를 막기 (JobParametersValidator)
- [ ] **Partitioning** - 데이터를 파티셔닝해서 병렬 처리 (대량 데이터 성능 최적화)
- [ ] **테스트 코드 작성** - @SpringBatchTest를 사용한 Job/Step 단위 테스트

### 실무 적용

- [ ] **실제 DB 연동** - H2 → MySQL/PostgreSQL로 전환, 메타 테이블 분리
- [ ] **외부 API 연동** - Mock → RestTemplate/WebClient로 실제 호출
- [ ] **스케줄링** - crontab / Jenkins / @Scheduled 연동
- [ ] **모니터링** - 실행 이력 조회, 실패 알림
