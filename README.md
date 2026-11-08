# hyundai-motors

현대자동차그룹 차량용 앱 개발 공모전 출품용 Pleos Connect 앱입니다. 급발진, 사고, 화재, 침수 상황을 진단하거나 직접 선택하면 대처 방법을 화면과 음성으로 안내하고, 대피 경로와 주변 도움 시설을 보여 줍니다.

> 시연용 프로토타입입니다. 진단 기준은 실차로 검증되지 않았고 안전 장치를 대신하지 않습니다. 신고 버튼은 앱 안에만 기록하는 임시 기능이며 전화, 문자, 네트워크로 아무것도 보내지 않습니다. 주변 시설은 실제로 존재하지 않는 샘플 데이터입니다.

## 상황별 화면

홈의 **위급 상황 직접 선택** 버튼이나 자동 감지로 열립니다. 각 화면에는 단계별 대처 안내(그림·음성), 대피 경로, 주변 도움 시설, 임시 신고 버튼이 있습니다.

| 상황 | 자동 감지 기준 | 대피 경로 | 주변 도움 시설 |
|---|---|---|---|
| 급발진 | 브레이크를 1.5초 이상 밟았는데 속도가 5km/h 이상 상승 | 안전 정차 구역 | 경찰서, 정비소, 병원 |
| 사고 | 0.75초 안에 35km/h 이상 감속, 또는 운행 중 에어백 전개 | 안전 정차 구역 | 병원, 경찰서, 소방서, 정비소 |
| 화재 | 실내 온도 50°C 이상이면서 1분 안에 10°C 이상 상승, 또는 엔진 과열 경고 | 대피 장소 | 소방서, 병원, 정비소 |
| 침수 | 감지 불가 (물 센서 API 없음), 직접 선택만 가능 | 고지대 대피 장소 | 소방서, 경찰서, 병원 |

임계값은 `EmergencyDetector.java`, 안내 문구와 시설 구성은 `EmergencyPlan.java`에 있습니다.

## 비상상황 확인 단계

감지되거나 버튼을 누르면 바로 대처 화면으로 가지 않고, 먼저 "○○ 상황인가요?"를 화면과 음성으로 묻습니다.

| 응답 | 결과 |
|---|---|
| "네", "맞아요", "도와주세요" 등 또는 **네** 버튼 | 대처 안내 화면 |
| "아니요", "괜찮아요", "취소" 등 또는 **아니요** 버튼 | 처음 화면 |
| 알아듣지 못함 | 한 번 더 질문, 그 뒤에는 버튼 대기 |
| 자동 감지 후 15초 무응답 | 대처 안내 화면 (운전자가 답하지 못하는 상황 대비) |

자동 감지된 급발진·화재가 5초 이상 정상으로 돌아오면 처음 화면으로 돌아갑니다. 사고는 차가 멈춘 것이 정상이라는 뜻이 아니므로 자동으로 닫히지 않습니다. 음성 인식은 Pleos SpeechToText를 먼저 쓰고, 실패하면 Android 음성 인식을 쓰고, 둘 다 없으면 버튼만 표시합니다. 답변 해석은 `AnswerClassifier.java`에 있습니다.

## 졸음운전 안내 (부가 기능)

| 상황 | 기준 | 안내 |
|---|---|---|
| 같은 방향 단조 주행 | 시속 60km 이상, 진행 방향 변화 10° 이내로 15분 | 환기와 주의 안내 |
| 장시간 운전 | 연속 2시간 (10분 이상 정차하면 다시 계산) | 휴게소·졸음쉼터 휴식 권고 |

음성과 홈 화면의 작은 알림으로만 안내하고, 한 번 안내하면 15분 동안 반복하지 않습니다. 비상상황 화면이 떠 있을 때는 동작하지 않습니다. 기준은 `DrowsinessMonitor.java`에 있으며, 진단 테스트 탭에서 20초짜리 시연을 할 수 있습니다.

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 속도 | AAOS Car API `PERF_VEHICLE_SPEED` |
| 브레이크, 기어, 에어백 전개 | Pleos Vehicle SDK |
| 실내 온도 | Pleos Vehicle SDK HVAC (에뮬레이터에서 값이 오지 않음, 단위 °C 가정) |
| 경고등 | Pleos Vehicle SDK `registerTelltales` (에뮬레이터에서 권한 거부되어 미동작) |
| 위치 | Pleos FusedLocation SDK |
| 경로 | Pleos NaviHelper SDK |
| 음성 | Pleos TextToSpeech SDK, 실패 시 Android TTS |

## 시연 방법

- **직접 선택:** 홈의 급발진·사고·화재·침수 버튼
- **자동 감지 재현:** **진단 테스트** 탭의 시나리오 버튼 (시연용 데이터 사용)

실제 차량에서 위급 상황을 유도하지 마세요.

## 개발 환경

| 항목 | 값 |
|---|---|
| Android Studio | 2026.2.2 |
| Gradle / AGP | 9.9.0 / 9.5.1 |
| 언어 / UI | Java 17 / XML 레이아웃 (Material 3) |
| minSdk / targetSdk / compileSdk | 28 / 35 / 37 |
| Gradle JDK | Android Studio 내장 JBR |

프로젝트 경로에 한글을 넣지 마세요. 버전은 `gradle/libs.versions.toml`에서 관리합니다.

## 실행 방법

1. Android Studio로 저장소를 열고 Gradle 동기화를 기다립니다.
2. [Pleos Connect Emulator](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 설치하고 AVD를 실행합니다.
3. `app`을 Run 합니다.

## 디자인 기준

[Pleos Connect UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)를 따릅니다.

| 항목 | 적용 |
|---|---|
| 글꼴 | Asta Sans (Pleos 시스템 글꼴, 42dot, OFL). 라이선스는 `licenses/AstaSans-OFL.txt` |
| 글자 크기 | Headline 48, Title 32~36, Body 26~28, Label 20~24 (sp) |
| 아이콘 | Material Symbols Rounded (Apache 2.0) |
| 간격 | 8dp 단위, 화면 여백 32dp, 터치 영역 80dp 이상 |

크기와 색상은 `res/values/themes.xml`, `dimens.xml`, `colors.xml`에 모여 있습니다.

### 단계별 반복 애니메이션

대처 안내의 각 단계 그림이 행동을 따라 짧게 반복해서 움직입니다. 예를 들어 브레이크는 눌렀다 떼고, 비상등은 깜빡이고, 대피는 멀어지고, 고지대 대피는 위로 올라갑니다. 단계와 동작의 짝은 `EmergencyPlan.java`, 동작 구현은 `StepAnimator.java`에 있습니다.

Pleos 가이드의 "운전 중 애니메이션으로 시선을 유도하지 않는다" 원칙에 따라 **정차 중에만** 움직입니다. 주행 중, 화면이 보이지 않을 때, 시스템에서 애니메이션을 끈 경우에는 그림이 멈춰 있습니다.

## 레이아웃 구조

| 창 크기 | 구성 |
|---|---|
| 폭 840dp 미만 | 한 열, 하단 내비게이션 |
| 폭 840dp 이상 | 왼쪽 내비게이션 + 핵심/상세 두 패널 |
| 주행 중 | 상세 패널을 숨기고 핵심 정보와 주요 동작만 표시 |

앱 창은 Pleos Connect에서 이동·크기 변경이 가능해서, 기기 방향이 아니라 **창 크기**를 기준으로 화면을 나눕니다.
미리보기는 [docs/layout](docs/layout)에 있습니다.

## 파일 위치

| 경로 | 내용 |
|---|---|
| `MainActivity.java`, `EmergencyOverlay.java` | 화면 표시 |
| `MainViewModel.java` | 진단, 안내 재생, 상태 유지 |
| `telemetry/` | 실제 차량 데이터와 시연 시나리오 |
| `diagnosis/` | 진단 규칙과 대처 단계 |
| `guidance/` | 음성 안내 |
| `facility/` | 샘플 시설과 경로 요청 |
| `report/` | 모의 신고 |
| `data/` | 주행 상태 인터페이스와 임시 구현 |
| `res/layout/` | 좁은 창 레이아웃 |
| `res/layout-w840dp/` | 넓은 창 레이아웃 |
| `res/values/themes.xml`, `colors.xml`, `dimens.xml` | 색상, 글자 크기, 터치 영역 |
| `res/values/strings.xml` | 화면 문구 |

## 임시로 둔 부분

- 주행 상태는 화면의 전환 버튼으로 바꿉니다. 제출 전에 `CarUxRestrictionsManager` 구현으로 교체하고 버튼을 지워야 합니다.
- `SampleFacilityRepository`의 시설은 가상 데이터입니다. 실제 장소 검색 API로 교체해야 합니다.
- `MockEmergencyReporter`는 기록만 합니다. 실제 신고 연동은 구현하지 않았고, 전화·문자 코드가 들어가면 `ManifestSafetyTest`가 실패합니다.
- 대처 문구는 일반 안내입니다. 대상 차량 취급설명서와 전문가 검토가 필요합니다.
- 진단은 앱이 실행 중일 때만 동작합니다.
- 색상과 크기는 Pleos 공식 디자인 토큰이 아닙니다.

## APK

제출용 APK는 `apk/팀명_앱이름_release.apk`로 저장합니다. keystore와 시크릿은 커밋하지 마세요.
