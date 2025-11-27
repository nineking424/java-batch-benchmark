# Oracle JDBC Batch vs MyBatis Batch 성능 비교 벤치마크

Oracle 데이터베이스 환경에서 순수 JDBC Batch 방식과 MyBatis ExecutorType.BATCH 방식의 INSERT 성능을 비교하는 벤치마크 프로젝트입니다.

## 개요

이 프로젝트는 대용량 데이터 INSERT 시 다음 세 가지 방식의 성능을 측정하고 비교합니다:

| 테스트 방식 | 설명 |
|------------|------|
| **JDBC Batch** | PreparedStatement.addBatch() + executeBatch() 직접 사용 |
| **MyBatis BATCH** | ExecutorType.BATCH 모드로 세션 생성 후 flushStatements() 사용 |
| **MyBatis Single** | ExecutorType.SIMPLE로 단건씩 INSERT (비교 기준) |

## 사전 요구사항

- Java 17+
- Maven 3.6+
- Oracle Database (XE, Standard, Enterprise)

## 데이터베이스 준비

Oracle에서 벤치마크용 사용자를 생성합니다:

```sql
CREATE USER benchmark_user IDENTIFIED BY benchmark_password;
GRANT CONNECT, RESOURCE TO benchmark_user;
GRANT CREATE TABLE, CREATE SEQUENCE TO benchmark_user;
ALTER USER benchmark_user QUOTA UNLIMITED ON USERS;
```

## 설정

`src/main/resources/application.properties` 파일에서 데이터베이스 접속 정보를 수정합니다:

```properties
# 데이터베이스 연결
db.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
db.username=benchmark_user
db.password=benchmark_password

# 벤치마크 설정
benchmark.recordCount=10000
benchmark.batchSize=1000
benchmark.warmupIterations=2
benchmark.testIterations=5
```

## 실행 방법

```bash
# 프로젝트 빌드
mvn clean compile

# 벤치마크 실행
mvn exec:java -Dexec.mainClass="com.example.benchmark.BatchBenchmark"
```

## 예상 결과 예시

```
╔══════════════════════════════════════════════════════════════╗
║ JDBC Batch INSERT                                            ║
╠══════════════════════════════════════════════════════════════╣
║ Records: 10,000     Batch Size: 1,000     Iterations: 5      ║
╠══════════════════════════════════════════════════════════════╣
║ Average Time    :          510.00 ms                         ║
║ Median Time     :          508.00 ms                         ║
║ Min Time        :          498 ms                            ║
║ Max Time        :          523 ms                            ║
║ Std Deviation   :           10.25 ms                         ║
║ Throughput      :       19,607.84 records/sec                ║
╚══════════════════════════════════════════════════════════════╝

JDBC Batch vs MyBatis BATCH:
├── JDBC Average      : 510.00 ms
├── MyBatis Average   : 580.00 ms
├── Difference        : 70.00 ms (13.73%)
└── Winner            : JDBC Batch
```

## 프로젝트 구조

```
java-batch-benchmark/
├── pom.xml
├── README.md
├── PRD.txt
└── src/main/
    ├── java/com/example/benchmark/
    │   ├── BatchBenchmark.java           # 메인 클래스
    │   ├── config/
    │   │   └── DatabaseConfig.java       # DB/MyBatis 설정
    │   ├── domain/
    │   │   └── Item.java                 # 도메인 객체
    │   ├── mapper/
    │   │   └── ItemMapper.java           # MyBatis Mapper 인터페이스
    │   ├── result/
    │   │   └── BenchmarkResult.java      # 결과 통계 클래스
    │   └── test/
    │       ├── JdbcBatchTest.java        # JDBC Batch 테스트
    │       └── MyBatisBatchTest.java     # MyBatis Batch 테스트
    └── resources/
        ├── application.properties
        ├── mybatis-config.xml
        ├── logback.xml
        └── mapper/
            └── ItemMapper.xml            # SQL 매핑
```

## 최적화 팁

1. **배치 사이즈 조정**: 일반적으로 500~2000 사이가 적절
2. **fetchSize 설정**: Oracle의 경우 defaultRowPrefetch 조정
3. **커넥션 풀**: HikariCP의 maximumPoolSize를 적절히 설정
4. **Oracle 설정**: `rewriteBatchedStatements=true` (MySQL의 경우)

## 결론 및 권장사항

- **대용량 INSERT**: JDBC Batch 또는 MyBatis BATCH 사용 권장
- **성능 우선**: 순수 JDBC Batch가 약간 더 빠름
- **개발 편의성**: MyBatis BATCH가 유지보수에 유리
- **단건 INSERT**: 성능이 중요한 경우 반드시 피할 것

## 라이선스

MIT License
