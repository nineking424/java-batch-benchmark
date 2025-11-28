# Kubernetes Deployment Guide

쿠버네티스 환경에서 Java Batch Benchmark를 실행하기 위한 가이드입니다.

## 파일 구성

```
k8s/
├── README.md           # 이 문서
├── deploy.sh           # 배포 자동화 스크립트
├── configmap.yaml      # 애플리케이션 설정
├── secret.yaml         # DB 인증 정보
├── job.yaml            # 벤치마크 Job 및 CronJob
└── oracle-db.yaml      # Oracle DB 테스트 환경 (선택)
```

## 사전 요구사항

- Kubernetes 클러스터 (1.25+)
- kubectl CLI 도구
- Docker 또는 Podman (이미지 빌드용)
- Oracle Database 접속 정보 (또는 테스트용 Oracle 컨테이너 사용)

## 빠른 시작

### 1. Docker 이미지 빌드

```bash
# 프로젝트 루트에서 실행
docker build -t java-batch-benchmark:1.0.0 .

# 또는 deploy.sh 사용
./k8s/deploy.sh --build
```

### 2. 이미지 레지스트리에 푸시 (필요시)

```bash
# 태그 지정
docker tag java-batch-benchmark:1.0.0 your-registry.com/java-batch-benchmark:1.0.0

# 푸시
docker push your-registry.com/java-batch-benchmark:1.0.0
```

### 3. Secret 설정

실제 DB 인증 정보로 `secret.yaml`을 수정하세요:

```bash
# Base64 인코딩
echo -n 'your_username' | base64
echo -n 'your_password' | base64

# secret.yaml 수정
kubectl apply -f k8s/secret.yaml
```

### 4. 배포 및 실행

```bash
# 전체 배포 (Oracle DB 포함)
./k8s/deploy.sh --with-oracle --build --run

# 또는 외부 Oracle DB 사용시
./k8s/deploy.sh --build --run
```

## 수동 배포

```bash
# 1. Namespace 생성
kubectl apply -f k8s/job.yaml  # namespace 정의 포함

# 2. ConfigMap 및 Secret 배포
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# 3. (선택) Oracle DB 배포
kubectl apply -f k8s/oracle-db.yaml

# 4. 벤치마크 Job 실행
kubectl apply -f k8s/job.yaml
```

## 설정 커스터마이징

### ConfigMap 수정

`configmap.yaml`에서 벤치마크 파라미터를 조정할 수 있습니다:

```yaml
data:
  DB_URL: "jdbc:oracle:thin:@//your-oracle-host:1521/XEPDB1"
  BENCHMARK_RECORD_COUNT: "50000"    # 테스트 레코드 수
  BENCHMARK_BATCH_SIZE: "2000"       # 배치 크기
  BENCHMARK_WARMUP_ITERATIONS: "3"   # 워밍업 반복 횟수
  BENCHMARK_TEST_ITERATIONS: "10"    # 테스트 반복 횟수
  JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC"
```

### 외부 Oracle DB 연결

외부 Oracle DB를 사용할 경우:

1. `configmap.yaml`에서 `DB_URL` 수정
2. `secret.yaml`에서 인증 정보 수정
3. `job.yaml`의 init container에서 DB 호스트 확인

```yaml
# configmap.yaml
data:
  DB_URL: "jdbc:oracle:thin:@//external-oracle.example.com:1521/ORCL"
```

## 모니터링

### 로그 확인

```bash
# 실시간 로그
kubectl logs -f job/java-batch-benchmark -n benchmark

# 또는
./k8s/deploy.sh --logs
```

### 상태 확인

```bash
# Pod 상태
kubectl get pods -n benchmark

# Job 상태
kubectl get jobs -n benchmark

# 전체 상태
./k8s/deploy.sh --status
```

### Job 결과 확인

```bash
# Job 완료 확인
kubectl get jobs -n benchmark -o wide

# 완료된 Job의 로그
kubectl logs job/java-batch-benchmark -n benchmark
```

## CronJob 설정

정기적인 벤치마크 실행을 위해 CronJob이 포함되어 있습니다:

```bash
# CronJob 상태 확인
kubectl get cronjobs -n benchmark

# 수동 트리거
kubectl create job --from=cronjob/java-batch-benchmark-scheduled manual-run -n benchmark
```

기본 스케줄: 매일 오전 2시 (UTC)

## 리소스 정리

```bash
# 전체 정리
./k8s/deploy.sh --clean

# 또는 수동
kubectl delete namespace benchmark
```

## 트러블슈팅

### Oracle DB 연결 실패

```bash
# Oracle Pod 상태 확인
kubectl get pods -n benchmark -l app=oracle-db

# Oracle 로그 확인
kubectl logs -l app=oracle-db -n benchmark

# Init container 로그 확인
kubectl logs job/java-batch-benchmark -n benchmark -c wait-for-db
```

### 이미지 Pull 실패

```bash
# 이미지 확인
kubectl describe pod -l app=java-batch-benchmark -n benchmark

# 로컬 이미지 사용시 (Minikube)
eval $(minikube docker-env)
docker build -t java-batch-benchmark:1.0.0 .
```

### 메모리 부족

`job.yaml`에서 리소스 limit을 조정하세요:

```yaml
resources:
  limits:
    memory: "4Gi"
    cpu: "4000m"
```

## 프로덕션 고려사항

1. **Secret 관리**: HashiCorp Vault 또는 AWS Secrets Manager 사용 권장
2. **이미지 레지스트리**: 프라이빗 레지스트리 사용
3. **리소스 모니터링**: Prometheus/Grafana 연동
4. **로그 수집**: ELK Stack 또는 Loki 사용
5. **Oracle DB**: Oracle Cloud 또는 관리형 서비스 사용
