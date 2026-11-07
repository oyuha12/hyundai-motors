# hyundai-motors

현대자동차그룹 차량용 앱 개발 공모전 출품용 Pleos Connect 앱입니다. 차량 데이터로 급발진 의심 등 위급 상황을 진단하고, 대처 방법을 화면과 음성으로 안내합니다.

> 시연용 프로토타입입니다. 진단 기준은 실차로 검증되지 않았고 안전 장치를 대신하지 않습니다. 신고 기능은 모의 기능이라 외부로 아무것도 전송하지 않습니다. 주변 시설은 실제로 존재하지 않는 샘플 데이터입니다.

## 주요 기능

| 기능 | 구현 |
|---|---|
| 위급 상황 진단 | 급발진 의심, 제동 이상, 충돌 의심, 과열, 동력계·안전 장치 경고 |
| 대처 안내 | 단계별 그림 표시와 음성 자동 재생 |
| 위급 경로 | 상황에 맞는 목적지를 정해 차량 내비게이션으로 안내 |
| 모의 신고 | 터치 한 번으로 앱 메모리에만 기록 |
| 주변 시설 | 소방서, 경찰서, 병원, 정비소, 안전 정차 구역 목록과 경로 |

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 속도 | AAOS Car API `PERF_VEHICLE_SPEED` |
| 브레이크, 기어, 전력 경고 | Pleos Vehicle SDK |
| 경고등 | Pleos Vehicle SDK `registerTelltales` (Pleos 에뮬레이터 이미지에서 권한 거부되어 미동작) |
| 위치 | Pleos FusedLocation SDK |
| 경로 | Pleos NaviHelper SDK |
| 음성 | Pleos TextToSpeech SDK, 실패 시 Android TTS |

가속 페달 값을 읽는 API가 없어서 급발진은 **브레이크를 밟은 채 속도가 오르는 현상**으로 판단합니다. 임계값은 `EmergencyDetector.java`에 있습니다.

## 시연 방법

앱의 **진단 테스트** 탭에서 시나리오를 누르면 시연용 데이터로 위급 상황을 재현합니다. 실제 차량에서 위급 상황을 유도하지 마세요.

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
- `MockEmergencyReporter`는 기록만 합니다. 실제 신고 연동은 구현하지 않았습니다.
- 대처 문구는 일반 안내입니다. 대상 차량 취급설명서와 전문가 검토가 필요합니다.
- 진단은 앱이 실행 중일 때만 동작합니다.
- 색상과 크기는 Pleos 공식 디자인 토큰이 아닙니다.

## APK

제출용 APK는 `apk/팀명_앱이름_release.apk`로 저장합니다. keystore와 시크릿은 커밋하지 마세요.
