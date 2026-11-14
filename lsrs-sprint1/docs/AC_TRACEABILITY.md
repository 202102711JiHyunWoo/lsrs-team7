# Sprint 1 요구사항·AC 추적표

## 기준 자료와 범위 결정

| 자료 | 기준 |
|---|---|
| `7조 시스템 구조 설계 및 개발 환경.pdf` | v1.0, 최종 수정 2026-11-05, 12쪽 |
| `[SE01]7조_Product Backlog.docx (2).pdf` | v1.0, 수정 2026-10-22, 18쪽, 총 16개 스토리/77 SP |
| `소프트웨어공학 7조 문제정의서 (3).pdf` | 사용자가 최종 제출한 문제 정의, 5쪽 |

구현 범위는 Backlog **4.1 Release Plan, PDF 17쪽**의 Sprint 1입니다. 구버전의 28개 스토리 목록을 사용하지 않았습니다. 아래 ‘AC’는 원문의 조건을 요약한 것으로 원문을 수정하거나 새로운 팀 합의를 꾸며 낸 것이 아닙니다. 별도 요구사항 명세서는 제공되지 않아 원본의 ‘FR38’ 등의 문구를 38개 요구사항 전부 구현했다는 근거로 사용하지 않았습니다.

## AC별 구현과 검증

| ID / SP | AC 요약 | 구현 근거 | 실제 검증 근거 |
|---|---|---|---|
| US-01 / 5 | 유효한 가입 정보로 계정 생성·인증 안내 | AuthService, AuthController, 이메일 화면 | `AuthIntegrationTest.US01_signupCreatesPendingHashedAccountAndLocalEmail`; 브라우저 가입 |
| US-01 | 유효한 이메일 인증으로 활성화 | 단일 사용 해시 토큰, 30분 기한 | `US01_validVerificationActivatesAccount`; 실제 로컬 SMTP 토큰 인증 |
| US-01 | 중복 이메일·필수값 오류 거부 | DTO 검증, 정규화, DB UNIQUE | normalizedDuplicateEmail, duplicateStudentNumber, missingRequiredFields 테스트 |
| US-01 | 미인증·무효 인증 차단 | PENDING 로그인 거부, 만료/소비 검증 | unverifiedAccount, invalidToken, tokenExpiresExactlyAtDeadline, usedToken 테스트 |
| US-02 / 5 | 올바른 인증 계정 로그인 | BCrypt + JWT, 이메일/학번 허용 | `US02_loginAcceptsEmailAndStudentNumber` |
| US-02 | 학생·관리자 기능 권한 구분 | SecurityFilterChain + 서비스 역할 검사 | studentCannotReadOrChangeAdminPolicies, arbitraryAdminSignup 테스트; 브라우저 403 |
| US-02 | 미인증·잘못된 토큰 차단 | 서명·발급자·만료·현재 DB 계정 검사 | missingAuthentication, forgedToken, wrongIssuer, expiredToken, 정확히 60분 만료·exp 누락 테스트 |
| US-03 / 5 | 날짜·시간대별 상태 구분 | SeatQueryService, 48석 그리드 | `US03_gridContainsAllFourDisplayStatesWithoutPrivateData`; 브라우저 48석 |
| US-03 | 타인의 예약·입실·반환 변경 반영 | 5초 폴링, 같은 겹침 조회 | 예약/체크인/퇴실 API 통합 검사, 브라우저 반환 후 가용 확인 |
| US-03 | 가용 좌석 없음 안내 | availableCount=0, 안내 문구, 버튼 비활성화 | `US03_fullyBookedGridReportsZeroAvailability`; 브라우저 전체 예약 UI 응답 fixture |
| US-03 | 잘못된 시간·조회 실패를 가용으로 표시하지 않음 | 서버 400, 실패 시 그리드 제거·‘—’·재조회 | invalidQuery, missingOrMalformedQuery 테스트; 브라우저 네트워크 차단 검증 |
| US-04 / 2 | 평상시 최대 4시간·30분 기준 표시 | DB PolicyVersion, 정책 스트립 | PolicyIntegrationTest, 브라우저 NORMAL 표시 |
| US-04 | 시험기간 최대 3시간·현재 정책 반영 | NORMAL/EXAM 전환과 새 버전 | adminEXAM 관련 테스트; 브라우저 관리자 변경→학생 재조회 |
| US-04 | 정책 변경 시 안내와 검증 일치 | policyVersion 일치 검사, 최신 maxMinutes 검증 | stale policy 409, EXAM 3시간 허용·초과 거부 |
| US-06 / 5 | 30분 단위 좌석·시간대 예약 | POST reservations, 같은 KST 날짜/경계/정책 검증 | validReservation, invalidIntervals(7개 입력), exactNormalMaximum, pastStart 테스트 |
| US-06 | 확정 후 예약 좌석·시간·기한 표시 | DB 커밋 후 201, 내 이용 현황 | 브라우저 예약 확인→RESERVED; 예약 스냅샷 테스트 |
| US-07 / 8 | 같은 학생의 겹치는 예약 차단 | User 잠금 후 겹침 재검사 | sameStudentCannotReserveOverlappingDifferentSeats |
| US-07 | 같은 좌석의 겹치는 예약 차단 | Seat 잠금 후 겹침 재검사 | differentStudentCannotReserveSameSeatOverlap |
| US-07 | 여러 학생의 같은 좌석 동시 요청에서 중복 0 | User→Seat 잠금, READ_COMMITTED | 20명 경합 ×3회, 매회 성공 1/충돌 19/중복 0 |
| US-07 | 같은 학생의 여러 좌석 동시 요청에서 중복 0 | 동일 사용자 행으로 직렬화 | 20개 좌석 경합 ×3회, 매회 성공 1/충돌 19/중복 0 |
| US-12 / 8 | 유효 QR·기한 내 웹 체크인 | 카메라/이미지 jsQR + 서버 토큰·기한 검사 | generatedPngDecodesAndChecksInReservedSeat; 브라우저 이미지·가상 카메라 |
| US-12 | 잘못된 QR·타인 예약 거부 | 좌석 해시 대조, 소유권 검사 | wrongQr, validQrForDifferentSeat, otherStudentCannotCheckIn 테스트 |
| US-12 | 취소되거나 기한 지난 예약 거부 | 상태·시각을 서버에서 재검증 | autoCancelled, exactDeadline, oneNanosecondAfterDeadline, beforeStart 테스트 |
| US-12 | 반복 체크인 시 중복 전이 없음 | CHECKED_IN 결과를 그대로 반환 | duplicateCheckIn, 10개 동시 체크인; 퇴실과의 경합 ×3회 |
| US-12 | 카메라 권한/연결 실패를 성공 처리하지 않음 | 권한 오류·대체 이미지·재시도 안내 | 브라우저 NotAllowedError 주입 후 RESERVED 유지 확인; 물리 카메라는 미검증 |
| US-10 / 3 | 본인 사용 중 예약 퇴실·남은 시간 반환 | CHECKED_IN→COMPLETED, 활성 겹침 제외 | exitReleasesSeatAndKeepsOriginalReservationTime |
| US-10 | 다른 학생 조회에 반환 반영 | 동일 좌석 조회 서비스 | 퇴실 통합 테스트, 브라우저 C03 재활성화 |
| US-10 | 타인·종료 예약 오류, 다른 예약 유지 | 소유권·상태 재검사 | otherStudentCannotExitAndDuplicateExitIsRejected, exitPreservesFollowingStudentsReservation, 10개 동시 퇴실 |

테스트 이름을 줄인 행은 `src/test/java/kr/ac/cnu/lsrs/` 안에서 해당 문자열을 검색하면 찾을 수 있습니다. 브라우저 검증은 `scripts/e2e.cjs`, SMTP/재시작 검증은 `scripts/runtime_checks.py`입니다.

## 원본의 모호함에 적용한 해석

1. **US-06 본문·AC 중복:** 원본 제목은 ‘30분 단위 좌석·시간대 예약’이지만 본문과 AC는 US-04 정책 안내와 중복됩니다. 제목, 문제정의서의 자율 시간 지정 예약, 구조 설계서의 `POST /api/reservations` 및 시간·동시성 규칙을 기준으로 실제 예약을 구현했습니다. 정책 안내·검증도 유지했습니다. 이 해석은 구현을 진행하기 위한 판단이며 교수자/팀의 새 승인으로 표시하지 않습니다.
2. **체크인 기한의 기준 시각:** Backlog US-12의 ‘예약 후 15분’ 대신 더 최근 구조 설계서의 ‘시작 ≤ 현재 ≤ 입실 기한, 현재 < 종료; 기한=시작+유예’를 적용했습니다. 미리 미래 시간대를 예약할 수 있는 설계와 일치합니다. 기한 정확히 일치하면 허용하고 1ns 뒤면 거부합니다.
3. **실시간:** 별도 전송 방식이 지정되지 않아 5초 폴링을 적용했습니다. 브라우저가 숨겨지거나 확인창이 열린 동안 불필요한 재조회를 줄입니다. 확정 요청에서는 DB를 다시 검증합니다.
4. **QR 등록 방식:** 별도의 좌석 QR 관리 요구가 없어 초기 48석에 안전한 토큰을 생성하고 관리자 전용 다운로드를 제공했습니다. 이미지 선택은 카메라 없는 PC 시연·권한 오류의 대체 입력이며 서버 인증 절차는 동일합니다.
5. **좌석 규모:** 개발용 기본 좌석 48개를 seed합니다. 성능 검증 US-05는 Sprint 2이므로 이 결과로 전체 확장 성능 합격을 주장하지 않습니다.

## Sprint 2로 남겨 둔 항목

| ID | 이번 상태 | 이어서 할 일 |
|---|---|---|
| US-05 | 48석·로컬 HTTP 100회 탐색 측정만 수행 | 원본 성능 환경·데이터 규모·변경 상황에 맞춘 정식 판정 |
| US-08 | S1에 필요한 본인 목록/상세/진행 상태만 제공 | 별도 예약 정보 기능의 전체 AC 검토·화면 확장 |
| US-09 | 미구현 | 연장 정책·다음 예약 경합·원자적 변경 |
| US-11 | 종료 시간이 겹치지 않는 다음 구간은 가용, 상태 정리 작업 없음 | 시간 만료 Job, 반복 실행 안전성 |
| US-13 | 기한 후 체크인 차단만 구현 | 자동 취소·해제 Job, 체크인과 경합 처리 |
| US-14 | 제한 필드·신규 예약 차단 검증만 준비 | 노쇼 이벤트 누적·제재 확정·중복 처리 방지 |
| US-15 | US-04를 위한 NORMAL/EXAM 전환만 제공 | 최대 이용 시간 등 수치 편집·검증·전체 UI |
| US-16 | 기본 정책 수치 DB 분리 | 유예·횟수·제한 일수 편집, Job/제재 일관성 |

Sprint 1은 총 41 SP 구현 범위입니다. 전체 제품 77 SP나 Sprint 2 36 SP까지 완료한 것으로 표시하지 않습니다.
