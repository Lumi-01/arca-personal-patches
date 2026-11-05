# 본문과 댓글의 배치 보정

## 0.3.16 본문을 일반 목록 항목으로 배치

사용자가 남은 현상을 **게시글 본문 이미지·내용이 댓글 위에 겹침**으로 확인했다. 0.3.15의 위치 값 동기화만으로 증상이 지속되어 본문의 stickyHeader 등록을 일반 item 등록으로 변경했다. 추가 이동량은 0으로 두어 본문과 댓글을 같은 LazyColumn의 배치 결과로 이동시킨다.

본문 항목은 기존 Compose PinnableContainer로 유지한다. 화면 밖으로 스크롤했을 때도 현재 글의 WebView가 다시 생성되는 일을 피하도록 했으며, 글 화면의 composition이 종료되면 핀을 해제한다. 핀은 화면 고정이 아니라 항목의 composition 수명 유지에 사용된다. 동영상 재생과 복귀 시 상태 유지의 실기 동작은 확인이 필요하다.

Morphe 설정의 **본문·댓글 겹침 방지**는 기존 `sync_body_offset` 저장값을 사용하며 기본 켜짐이다. 변경 후 앱을 재실행해야 한다. 꺼진 경우 원본 stickyHeader와 이동량 계산을 사용한다. `BodyOffset`을 제거하고 `BodyPin`으로 교체했다.

패치 대상 버전은 제한 없는 단일 AppTarget으로 선언한다. 기존 혼합 선언이 사용하던 호환성 정보와 달리 구형 패처에서도 버전 번호로 제외되지 않도록 했다. 코드 구조 검사에서 실패한 버전은 대응 코드 검토가 필요하다.

번들 빌드와 Plus 0.9.32768 원본 APK 패치 적용·재빌드·서명이 통과했다. 반복 화면 테스트 중지 요청을 유지하므로 실기 겹침 해결과 미디어 회귀 여부는 미확인이다.

## 0.3.15 기록

사용자는 0.3.14 이후에도 간헐적 겹침이 남는다고 보고했다. 특히 빠른 스크롤에서 빈도가 높다는 이전 설명을 바탕으로, 글자 높이와 별개인 본문 배치 경로를 검토했다. 현재 기기에서 동일 현상을 재현하거나 직접 원인을 확정한 것은 아니다.

## 확인한 구조

Plus 0.9.32768은 본문 WebView를 LazyColumn의 세 번째 stickyHeader로 유지한다. 첫 번째 표시 항목의 번호가 2보다 작으면 본문 이동량은 0, 2이면 음수 스크롤량, 2보다 크면 음수 본문 높이이다. 본문 뒤에는 평가 영역과 댓글이 배치된다.

이동량의 세 입력은 각각 snapshotFlow로 읽고 combine → stateIn(WhileSubscribed) → collectAsState를 통해 전달된다. [combine](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html)은 각 입력이 마지막으로 발행한 값을 결합하므로 현재 배치와 같은 시점의 조합이라는 보장이 없다. 빠른 스크롤이나 이미지 로딩에 따른 높이 변화와 전달 시점이 어긋나는 것이 이번 후보 원인이다. 댓글 내부 텍스트끼리 겹치는 문제까지 이것으로 설명하거나 해결했다고 주장하지 않는다.

## 변경

- `BodyOffset`이 읽기 전용 State 역할로 현재 목록 상태와 측정 높이를 직접 읽는다. 해당 소비 지점의 Compose snapshot에 속한 읽기이므로 별도 복사본의 비동기 전달을 기다리지 않는다. [Compose 상태 읽기 추적](https://developer.android.com/develop/ui/compose/phases)을 이용한다.
- 본문 이동 공식, stickyHeader, WebView, 댓글 내용/측정은 바꾸지 않는다.
- 기본 켜짐인 **빠른 스크롤 시 본문 위치 보정**은 앱 재실행 후 적용된다. 끄면 기존 collector 경로로 돌아간다. 실행 중 remember/collector 그룹 구조를 교체하지 않는다.
- 기존 flow 생성 코드는 유지하되 보정 경로에서는 구독하지 않는다. 기존 WhileSubscribed 작업은 입력을 수집하지 않으며 별도 타이머/폴링을 추가하지 않는다. 추후 그 생성 코드 제거는 레지스터 생존 범위와 Compose 그룹 균형 검토가 필요하다.
- 0.3.14의 무조건적인 intrinsic 높이 재측정은 0.3.10의 글꼴/방향 변경 시 무효화로 되돌린다. 글꼴 배율 검사는 유지한다.

## 유지보수와 검증 범위

`BodyOffsetPatch.kt`는 article composable의 문자열, collector 호출과 결과 레지스터, 다음 lifecycle 진입점, LazyListState와 높이 상태의 위치를 검사한다. 컴파일 전용 `B.E`, `V.s0`는 APK에 포함할 대상 앱 구현이 아니다. 앱 업데이트 시 q/r의 의미, 상태 필드, stickyHeader 인덱스 2와 원본 계산식도 다시 확인해야 한다. 시그니처 일치만으로 향후 버전 호환을 보장하지 않는다.

사용자 요청에 따라 반복 UI/재현 테스트는 중지 상태를 유지한다. 이번 변경의 빌드와 기준 APK 패치 적용은 검사하지만 런타임·빠른 스크롤·미디어·댓글 회귀 확인과 CPU/배터리 측정은 수행하지 않는다. 문제가 지속되면 본문이 댓글 위에 남는 경우와 댓글 텍스트가 이웃 댓글을 침범하는 경우를 구분해야 한다.

2026-10-31: Gradle `:patches:buildAndroid :patches:generatePatchesList` 성공. Morphe Desktop 1.18.1로 Plus 0.9.32768의 세 공개 패치 적용, APK 재빌드와 서명 성공(`failedPatches: []`). 생성한 0.3.15 APK는 에뮬레이터에 설치하지 않았다.
# Version filter regression

The 0.3.16 bundle also applies all three patches, rebuilds and signs an otherwise identical baseline APK with its version name changed to `9.9.99999`, without force mode. This checks removal of the version-number filter, not compatibility with unknown future bytecode. No UI reproduction or battery measurement was performed for this release.
