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
