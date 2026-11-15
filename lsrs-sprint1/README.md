# LSRS · 7조 Sprint 1

도서관 좌석 예약 시스템의 **Sprint 1 기본 예약·입실·퇴실 흐름**을 구현한 프로젝트입니다. 최신 제출본인 2026-11-05 시스템 구조 설계서와 2026-10-22 Product Backlog의 Sprint 1 **8개 스토리 / 41 SP**를 기준으로 삼았습니다.

Java 21 · Spring Boot · Spring Security/JWT · Thymeleaf/JavaScript · Spring Data JPA · H2 · Gradle Wrapper로 구성했습니다. 화면의 예약·체크인·퇴실은 실제 서버와 DB를 사용합니다.

Start-local 파일 기준으로 수정합니다.

## 1. 가장 빠르게 실행하기 — Windows

1. ZIP을 **압축 해제**합니다. ZIP 안에서 바로 실행하지 마세요.
2. JDK 21을 설치하고 `java -version`이 21인지 확인합니다.
3. 프로젝트 폴더의 **`start-local.bat`**를 실행합니다.
4. 서버 초기화가 끝날 때까지 잠시 기다린 뒤 **http://localhost:8080** 으로 접속합니다.

이미 빌드한 `dist/lsrs-sprint1.jar`를 포함하므로 시연 실행에는 Gradle·Node.js·Python 설치나 의존성 다운로드가 필요하지 않습니다. Java 21은 필요합니다. 종료할 때 실행 창에서 `Ctrl+C`를 누릅니다.

macOS/Linux에서는 프로젝트 폴더에서 `sh start-local.sh`를 실행합니다.

### 시연 계정

| 구분 | 이메일 | 비밀번호 | 용도 |
|---|---|---|---|
| 학생 | student@example.test | Demo1234! | 자유롭게 조회·예약·체크인·퇴실 |
| 관리자 | admin@example.test | Demo1234! | 평상시/시험기간 전환, 좌석 QR 다운로드 |
| 관리자 | jihyunwoo@example.test | | 
| 다른 학생 | other@example.test | Demo1234! | A02 사용 중 상태 시연 |
| 입실 대기 학생 | reserved@example.test | Demo1234! | A05 예약됨 상태 시연 |


start-demo 기준 (테스트 시연 체킹 용도)
**시연 모드의 시각은 2026-11-14 10:00, 한국 시간으로 고정됩니다.** 언제 실행해도 시작 시각·체크인 기한 때문에 시연이 막히지 않도록 만든 별도 모드입니다. 화면에도 고정 시각을 표시합니다. 시간 흐름에 따른 만료를 시연하는 모드는 아닙니다.

시연 DB는 `data/demo/`에 저장되므로 재실행해도 변경 사항이 남습니다. 완전히 초기화하려면 **서버를 종료한 후** `data/demo/` 폴더만 지웁니다. 첫 실행 시 A02·A05의 시연 예약이 생성됩니다. 다른 실행 창에서 같은 DB를 동시에 열지 마세요.

## 2. 예약부터 퇴실까지 직접 확인하기

1. 학생 계정으로 들어가 기본 날짜, **10:00–11:00**을 조회합니다.
2. 예약 가능한 **C03**을 선택하고 **예약하기 → 예약 확정**을 누릅니다.
3. 오른쪽 **내 이용 현황 → QR 체크인**을 누릅니다.
4. **QR 이미지 선택**으로 `demo/LSRS-C03-QR.png`를 선택합니다. 실제 QR 판독 후 서버 검증을 거쳐 **이용 중**으로 바뀝니다.
5. **퇴실하고 좌석 반환 → 퇴실 확정**을 누르면 좌석이 다시 예약 가능해집니다.

`demo/LSRS-C04-QR.png`를 잘못 선택하면 좌석 불일치가 안내되고 체크인되지 않습니다. 동봉한 QR 두 장은 **demo 모드 전용**입니다. 일반 local 모드의 QR은 관리자 화면에서 새로 받으세요.

카메라로 확인하려면 관리자 탭에서 C03 QR을 내려받아 인쇄하거나 다른 기기에 표시하고 학생 탭에서 **카메라로 QR 스캔**을 누릅니다. 브라우저 카메라 권한이 필요합니다. 카메라는 `localhost` 또는 HTTPS에서 사용할 수 있습니다. 이 구현의 기본 접속 주소는 실행 중인 컴퓨터의 localhost입니다.

학생·관리자 인증은 탭별 `sessionStorage`에 저장합니다. 새 탭에서 각각 로그인할 수 있고, 페이지를 새로고침해도 해당 탭의 로그인은 유지됩니다. 브라우저의 ‘탭 복제’는 기존 탭 저장소를 복사할 수 있으므로 다른 역할은 새 탭을 열어 로그인하세요.

## 3. 실제 시각으로 가입해서 사용하기

`start-local.bat` 또는 `sh start-local.sh`를 실행합니다. **실제 한국 시간**으로 동작하며, 시연 계정은 생성하지 않습니다. 기존 demo 서버와 같은 포트에서 동시에 실행할 수는 없습니다.

1. 회원가입에서 학생 학번(숫자 8–12자리), 이메일, 비밀번호를 입력합니다.
2. 비밀번호는 영문·숫자·특수문자를 포함한 8자 이상, UTF-8 72바이트 이내입니다.
3. 기본 개발 모드에서는 외부 발송 대신 http://localhost:8080/dev/mailbox 에 인증 메일을 저장합니다.
4. 메일함에서 가입한 이메일로 조회하고 **이메일 인증하기**를 누릅니다.
5. 인증 후 학번 또는 이메일로 로그인합니다. 미래의 30분 경계 시각을 선택해 예약하세요.

인증 링크는 30분간 유효하고 한 번만 사용할 수 있습니다. 재발송은 1분 후 가능합니다. 기본 개발용 관리자 가입 허용 이메일은 `admin@example.test`입니다. 가입 유형을 관리자로 선택하고 이 이메일을 인증하면 관리자 화면을 사용할 수 있습니다. 사전 허용되지 않은 이메일의 관리자 가입은 서버가 거부합니다.

일반 개발 DB는 `data/lsrs.mv.db`, 서명 키는 `data/jwt.key`에 저장됩니다. **DB를 보존할 때 키도 함께 보존**해야 로그인 토큰과 기존 좌석 QR이 유지됩니다. 시연 데이터와 일반 개발 데이터는 분리됩니다.

개발용 메일함은 로컬 개발 편의 기능이며 같은 컴퓨터에서만 열립니다. 실제 이메일 소유 확인을 시연·운영하려면 아래 SMTP 설정을 사용하세요.

## 4. 실제 SMTP 설정

PowerShell 예시입니다. 실제 서버의 계정·도메인·TLS 설정으로 바꾸세요.

```powershell
$env:LSRS_MAIL_MODE = "smtp"
$env:SMTP_HOST = "smtp.your-school.example"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "your-account"
$env:SMTP_PASSWORD = "your-app-password"
$env:SMTP_AUTH = "true"
$env:SMTP_STARTTLS = "true"
$env:SMTP_FROM = "library@your-school.example"
$env:LSRS_ADMIN_EMAILS = "admin@your-school.example"
$env:LSRS_PUBLIC_BASE_URL = "http://localhost:8080"
.\start-local.bat
```

발송 실패 시 계정은 `PENDING`으로 남고 실패 안내와 재발송 경로가 제공됩니다. SMTP 모드에서는 개발용 메일함이 열리지 않습니다. 외부 메일 사업자의 실제 수신·스팸 분류는 사용자의 SMTP 계정으로 최종 확인해야 합니다. 이번 검증에서는 외부 사람에게 메일을 보내지 않고 로컬 SMTP 서버로 전송 성공·실패를 확인했습니다.

## 5. 개발·빌드·자동 테스트

VS Code에서 프로젝트 폴더를 열고 권장 Java/Gradle 확장을 설치합니다. JDK 21을 선택하세요. Git 저장소에 올릴 때는 포함된 `.gitignore`를 사용하면 데이터·키·빌드 캐시가 제외됩니다.

Windows PowerShell:

```powershell
.\gradlew.bat clean test bootJar
java -jar build\libs\lsrs-sprint1.jar --spring.profiles.active=local
```

macOS/Linux:

```sh
./gradlew clean test bootJar
java -jar build/libs/lsrs-sprint1.jar --spring.profiles.active=local
```

첫 빌드에는 Gradle과 Maven 의존성 다운로드가 필요합니다. Wrapper 버전과 배포본 SHA-256을 고정했습니다. 소스를 바꾼 후에는 새 `build/libs/lsrs-sprint1.jar`를 직접 실행하거나 `dist/lsrs-sprint1.jar`로 복사하세요. 루트 실행 스크립트는 동봉된 `dist` 실행 파일을 우선합니다.

테스트 DB는 메모리 H2이며 사용자 DB를 수정하지 않습니다. 테스트마다 시간을 초기화해 기한 경계를 재현합니다.

| 검증 | 실행 방법 | 필요 사항 |
|---|---|---|
| 서버 통합·보안·동시성 | `gradlew.bat clean test bootJar` | JDK 21, 최초 다운로드 |
| 브라우저 전체 흐름 | `npm ci`, `npx playwright install chromium`, `npm run test:e2e` | Node.js 22 이상, Chromium 다운로드 |
| SMTP·재시작·HTTP 검증 | `python scripts/runtime_checks.py` | Python 3.10 이상, 빌드된 JAR |

브라우저/런타임 검증 스크립트는 각각 임시 DB를 생성하고 종료 후 삭제합니다. 기본 테스트 포트는 18088·18089입니다. 브라우저 카메라 검증에는 QR 영상을 입력하는 Chromium 가상 카메라를 사용합니다. 테스트 실패 시 `reports/`의 결과와 서버 로그를 확인하세요.

필요 시 `LSRS_JAVA`에 Java 실행 파일, `LSRS_BROWSER_EXECUTABLE`에 Chromium 실행 파일을 지정할 수 있습니다. 동봉된 테스트 결과는 `reports/junit/`, `reports/coverage/`, `reports/e2e-results.json`, `reports/runtime-results.json`에 있습니다. 상세 해석은 [테스트 보고서](docs/TEST_REPORT.md)를 참고하세요.

## 6. 구현 범위와 주요 기준

| 스토리 | 구현 |
|---|---|
| US-01 | 가입, 비밀번호 해시, 이메일 단일 사용 인증·재발송 |
| US-02 | JWT 로그인, 학생/관리자 및 예약 소유자 권한 검증 |
| US-03 | 날짜·시간대별 48석 그리드, 5초 갱신, 오류·가용 좌석 없음 안내 |
| US-04 | DB 정책 안내, 평상시 4시간/시험기간 3시간, 30분 단위 |
| US-06 | 같은 날짜 30분 경계의 시간 지정 예약, 정책·제한 검증 |
| US-07 | 동일 학생·좌석의 겹침 방지, 트랜잭션과 DB 행 잠금 |
| US-12 | 웹 카메라/이미지 QR 판독, 좌석 일치·기한·중복 체크인 검증 |
| US-10 | 본인 사용 중 예약의 조기 퇴실, 좌석 반환, 중복 처리 차단 |

체크인은 최신 구조 설계서의 **예약 시작 시각부터 시작+15분까지(기한 포함), 예약 종료 전** 규칙을 적용했습니다. 예: 10:00 시작 예약은 10:00–10:15 체크인 가능합니다. 이전 Backlog의 ‘예약 후’ 표현과 US-06 본문 중복에 관한 해석 근거는 [요구사항 추적표](docs/AC_TRACEABILITY.md)에 남겼습니다.

Sprint 2의 자동 노쇼 취소·누적 제재, 시간 만료 작업, 이용 연장, 정책 수치 전체 편집, 정식 성능 합격 판정은 이번 완료 범위에 포함하지 않았습니다. 특히 입실 기한을 넘기면 체크인은 거부되지만 **미입실 예약을 자동 취소·해제하는 스케줄러는 아직 없습니다.** 만료 후 상태 정리와 노쇼 처리는 Sprint 2에서 이어서 구현해야 합니다.

현재 이용 현황 조회와 운영 기간 전환은 Sprint 1 흐름을 위해 최소 제공했습니다. US-08·US-15·US-16 전체 완료를 의미하지 않습니다. 예약 제한 필드와 차단 검증은 준비했지만 노쇼 자동 누적·제재 배치는 구현하지 않았습니다.

## 7. 함께 볼 자료

- [구조·API 설명](docs/ARCHITECTURE.md): 요청이 DB까지 처리되는 방식과 동시 예약 방지 원리
- [요구사항/AC 추적표](docs/AC_TRACEABILITY.md): 제출 자료와 구현·검증 연결
- [테스트 보고서](docs/TEST_REPORT.md): 실제 결과, 경계값, 검증 한계
- [구현·수정 기록](docs/SPRINT1_LOG.md): 수행한 개발과 오류 수정
- `docs/references/`: 이번 구현의 기준으로 확인한 원본 PDF 3개

포트 충돌 시 기존 실행을 종료하거나 `java -jar dist/lsrs-sprint1.jar --spring.profiles.active=demo --server.port=8081 --app.public-base-url=http://localhost:8081`처럼 포트와 링크 주소를 함께 변경합니다. DB 잠금 오류가 나면 같은 DB를 사용 중인 기존 서버를 먼저 종료하세요.
