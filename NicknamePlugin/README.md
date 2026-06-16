# NicknamePlugin

마인크래프트 **Paper 26.1.2 / Java 25** 환경용 한글 닉네임 변경 플러그인입니다.

[md5lukas/AnvilGUI (Sytm fork)](https://codeberg.org/md5lukas/AnvilGUI) 라이브러리를 사용한 가상 모루 GUI 에서 닉네임을 변경하고,
그 결과를 **머리 위 닉네임(PacketEvents PLAYER_INFO_UPDATE) · 탭리스트(옵션) · `%nickname_*%` PlaceholderAPI 자리표시자**에 반영합니다.

> **설치 위치 주의:** 이 플러그인은 `org.bukkit.*` 기반 **백엔드 Paper 서버 플러그인**입니다.
> Velocity 프록시에 설치하는 플러그인이 **아닙니다.** 각 백엔드 Paper 서버의 `plugins/` 에 넣으세요.
> 멀티서버 동기화는 공유 MariaDB 로 처리하며, 프록시에는 아무것도 설치하지 않습니다.

> 채팅창 닉네임 가공 기능은 본 플러그인이 직접 담당하지 않습니다. 채팅 포맷은 다른 채팅 플러그인
> (EssentialsChat, DeluxeChat 등) + `%nickname_display%` PAPI 자리표시자로 처리하세요.

---

## 주요 기능

- `/닉네임` 명령어로 가상 모루 GUI 열기 (0번 슬롯=텍스트 입력, 1번=초기화, 2번=변경)
- 한글 2~5글자 닉네임만 허용 (정규식/길이 모두 `config.yml` 로 커스터마이징)
- 변경 시 인벤토리 내 **닉네임 변경권** 아이템 1개 소모
- **머리 위 닉네임**: PacketEvents 의 `PLAYER_INFO_UPDATE` 패킷을 가로채 `GameProfile.name` 만
  위장닉으로 교체. 위장닉 없는 플레이어는 vanilla 그대로 (진짜 닉 표시). 탭리스트는 별도 설정 그대로.
  서버 측 `player.getName()` / 채팅 / 권한 / 저장소 영향 없음. on/off 토글
- 탭리스트 표시명: 선택 사용
- `%nickname_display% / %nickname_real% / %nickname_fake% / %nickname_has_fake% / %nickname_status%` PAPI 자리표시자 제공
- 모든 메시지/아이템(머티리얼·커스텀모델·이름·로어·발광) 완전 커스터마이징
- 저장소 **YML / MariaDB(HikariCP)** 듀얼 백엔드, 핫 리로드 (`/닉네임 reload`)
- **멀티서버(Velocity 등) MariaDB 공유 동기화**: `updated_at` 폴링으로 다른 서버에서 바꾼 닉을 자동 반영
- 귓속말/우편 등 외부 플러그인 연동용 공개 **`NicknameAPI`** + `PlayerNicknameChangeEvent`

---

## 빌드

### 요구사항
- JDK **25**
- Maven 3.9+
- 인터넷 연결 (PaperMC / CodeMC / extendedclip 저장소)
- **사전 준비: `lib/anvilgui.jar`** — 아래 "AnvilGUI 사전 빌드" 참고

### AnvilGUI 사전 빌드 (최초 1회)

Paper 26.1+ 는 mojang-mapped 라 WesJD/AnvilGUI 원본이 호환되지 않습니다.
NMS 의존성을 제거한 [md5lukas fork (Codeberg)](https://codeberg.org/md5lukas/AnvilGUI) 를 직접 빌드해 사용합니다.

1. Codeberg 에서 소스 다운로드 후 압축 해제
2. `gradle/wrapper/gradle-wrapper.properties` 의 Gradle 버전을 **9.1.0** 으로 (Java 25 지원)
3. `settings.gradle.kts` 의 foojay-resolver 플러그인을 **version "1.0.0"** 으로
4. 빌드 (Spotless 는 Java 25 와 비호환이라 제외):
   ```bash
   .\gradlew.bat jar -x spotlessJava -x spotlessCheck -x spotlessApply
   ```
5. 생성된 `anvilgui/build/libs/anvilgui-2.0.0.jar` 을 이 프로젝트의 `lib/anvilgui.jar` 로 복사
6. local Maven repo 에 1회 설치 (네트워크 저장소엔 없는 fork 라 수동 설치 필요):
   ```bash
   mvn install:install-file -Dfile=lib/anvilgui.jar -DgroupId=net.wesjd \
       -DartifactId=anvilgui -Dversion=2.0.0-SNAPSHOT -Dpackaging=jar -DgeneratePom=true
   ```

### 명령
```bash
mvn clean package
```

빌드 산출물은 `target/NicknamePlugin-1.0.0.jar` 입니다.
이 JAR 안에 AnvilGUI, HikariCP, MariaDB JDBC 가 모두 셰이딩되어 들어가므로 **서버에 별도 설치할 필요가 없습니다.**
(단, PacketEvents 와 PlaceholderAPI 는 서버에 별도 설치 — 아래 선택 의존성 참고)

### IntelliJ IDEA
1. `File → Open` 으로 프로젝트 폴더 선택
2. JDK 25 SDK 지정 (`File → Project Structure → Project`)
3. 위 "AnvilGUI 사전 빌드" 의 install-file 1회 실행 (터미널 또는 Maven `Execute Maven Goal`)
4. 오른쪽 Maven 패널 → `Lifecycle → package` 실행
5. `target/` 폴더의 JAR 을 **백엔드 Paper 서버** `plugins/` 에 복사

---

## 설치

> **다시 강조: 백엔드 Paper 서버에 설치합니다. Velocity 프록시에는 설치하지 않습니다.**

### Velocity + Paper 구성

| 위치 | 무엇을 설치/설정 |
|---|---|
| **Velocity 프록시** | NicknamePlugin **설치 안 함**. Velocitab 사용 시 `remove_nametags: false` 만 확인 (아래 참고) |
| **각 백엔드 Paper 서버** | `NicknamePlugin-1.0.0.jar` + PacketEvents + PlaceholderAPI 설치. 모든 백엔드가 **같은 MariaDB** 를 바라보게 `config.yml` 설정 |

멀티서버 동기화는 공유 MariaDB 의 `updated_at` 폴링으로 처리되므로, 각 백엔드 서버의 `config.yml`
의 `storage.mariadb.*` 가 동일한 DB 를 가리키기만 하면 됩니다.

### 설치 절차

1. 빌드된 `NicknamePlugin-1.0.0.jar` 을 **각 백엔드 Paper 서버**의 `plugins/` 폴더에 넣고 서버 시작
2. 자동 생성된 `plugins/NicknamePlugin/config.yml`, `messages.yml` 을 편집
   - `storage.mariadb.password` 를 **실제 DB 비밀번호**로 교체 (기본값 `changeme` 는 샘플)
   - 모든 백엔드가 같은 host/database/table 을 가리키게
3. `/닉네임 reload` 로 핫 리로드

### 선택 의존성

| 플러그인 | 효과 |
|---|---|
| **PacketEvents** | 머리 위 닉네임(name-tag) 기능 활성화. 없으면 이 기능만 자동으로 꺼짐 |
| **PlaceholderAPI** | `%nickname_*%` 자리표시자 활성화 (채팅 플러그인 / Velocitab 의 prefix 등에서 사용) |

PacketEvents / PlaceholderAPI 가 없어도 GUI / 명령어 / MariaDB 동기화 등 핵심 기능은 정상 동작합니다.

---

## 명령어

| 명령어 | 권한 | 설명 |
|---|---|---|
| `/닉네임` | `nickname.use` | 닉네임 변경 GUI 열기 |
| `/닉네임 확인 <닉네임>` | `nickname.check` | 진짜닉/위장닉 상호 조회 |
| `/닉네임 지급 <닉네임> <수량>` | `nickname.admin` | 닉네임 변경권 지급 |
| `/닉네임 초기화 <닉네임>` | `nickname.admin` | 위장 닉네임 초기화 |
| `/닉네임 reload` | `nickname.admin` | 설정/메시지 리로드 |

기본 권한:
- `nickname.use` → 모두 (default: true)
- `nickname.check` → 모두 (default: true)
- `nickname.admin` → OP 만 (default: op)

---

## 머리 위 닉네임 (Name Tag) — 본 플러그인이 직접 처리

`config.yml`:
```yaml
name-tag:
  enabled: true       # 끄려면 false
```

**동작 방식**:
- 서버에 **PacketEvents** 플러그인 별도 설치 필요. 없으면 이 기능만 자동으로 꺼집니다.
- `PLAYER_INFO_UPDATE` 패킷의 `GameProfile.name` 필드만 위장닉으로 바꿔치기 → 머리 위에 위장닉 표시.
- 위장닉 없는 플레이어는 진짜 닉이 그대로 표시 (vanilla).
- **탭리스트는 영향 받지 않습니다.** `tab-list.enabled` 설정 그대로 동작 (`false` 인 경우 `displayName` 필드를 진짜 이름으로 덮어쓰는 보호 로직 동작).
- 서버 측 `player.getName()` / 채팅 / 권한 / 저장소 영향 없음.
- 위장닉 변경 시 viewer 들의 클라이언트에 hide/show 사이클 1회 (약 50~100ms 깜빡임).

### ⚠️ Velocitab 와 같이 쓰는 경우 필수 설정

Velocitab 의 `remove_nametags` 가 `true` 이면 Velocitab 의 스코어보드 팀(visibility=NEVER) 이 우리 패킷보다 뒤에 적용되어 **모든 nametag 를 가려버립니다.** 본 플러그인이 머리 위 닉네임을 다루는 경우 반드시:

```yaml
# Velocity 프록시의 plugins/velocitab/config.yml
remove_nametags: false
```

이렇게 설정해야 본 플러그인이 그린 위장닉이 클라이언트에 정상 표시됩니다.

**한글 호환성**: Minecraft 1.21.x 클라이언트는 유니코드 폰트라 한글 사용자명을 정상 렌더링.

---

## PlaceholderAPI 자리표시자

PlaceholderAPI 설치 시 자동 등록됩니다.

| 자리표시자 | 결과 |
|---|---|
| `%nickname_display%` | 위장닉 있으면 위장닉, 없으면 진짜닉 |
| `%nickname_real%` | 진짜 닉네임 (계정 이름) |
| `%nickname_fake%` | 위장 닉네임 (없으면 빈 문자열) |
| `%nickname_has_fake%` | `true` / `false` |
| `%nickname_status%` | 위장닉 문자열, 없으면 `없음` |

다른 채팅/탭리스트 플러그인(EssentialsChat, DeluxeChat, Velocitab 등)에서 위장닉을 표시하려면
포맷 안에 `%nickname_display%` 만 넣어 주면 됩니다.

---

## 외부 플러그인 연동 API

귓속말/우편/거래 같은 플러그인에서 위장닉으로도 대상을 찾고 이름을 표시하려면:

```java
// 1) 입력 문자열이 위장닉이어도 대상 찾기
Optional<Player> target = NicknameAPI.findOnlinePlayerByAnyName(args[0]);

// 2) 메시지에 쓸 이름 (true → 위장 우선, false → 항상 진짜)
String label = NicknameAPI.resolveForMessaging(sender, true);
```

위장닉이 바뀔 때마다 `PlayerNicknameChangeEvent` 가 발생하므로, 이걸 들어 캐시를 갱신하면 됩니다.

---

## 리소스팩 (0번 슬롯 아이템 숨기기)

모루 GUI 의 0번 슬롯은 텍스트 입력을 위해 반드시 아이템이 있어야 합니다.
플레이어에게는 이 아이템이 보이지 않아야 자연스럽기 때문에, **리소스팩으로 시각적으로 숨깁니다.**

기본 `config.yml` 에서는 변경권/슬롯 아이템 모두 `custom-model-data: 20000` / `20001` 을 사용합니다.
사용 중인 리소스팩의 `<glyph:...>` 와 `custom_model_data` 매핑을 거기에 맞춰 두면 됩니다.

---

## MariaDB 사용

`config.yml`:
```yaml
storage:
  type: mariadb
  mariadb:
    host: localhost
    port: 3306
    database: nickname
    username: root
    password: "비밀번호"
    pool-size: 10
    table-name: nicknames
    multi-server-sync:
      enabled: true
      poll-interval-seconds: 3
```

서버에 별도 JDBC 설치 불필요. MariaDB 와 HikariCP 는 JAR 안에 셰이딩되어 있습니다.

테이블은 첫 구동 시 자동 생성됩니다 (`uuid`, `real_name`, `fake_name`, `updated_at` + 인덱스).
**예전 스키마(인덱스 없음) 에서 업그레이드하는 경우**, 시작 시 `information_schema` 를 확인하고
`idx_updated` 가 없을 때만 `ALTER TABLE` 로 추가하므로 `Duplicate key name` 경고가 발생하지 않습니다.

MariaDB 연결 실패 시 자동으로 YML 백엔드로 폴백합니다 (로그에 경고 기록).

---

## 메시지 / 아이템 커스터마이징

- 모든 안내 메시지: `messages.yml` (MiniMessage 포맷 + `&` 레거시 + `&#RRGGBB` 헥스 + PAPI `%...%`)
- 변경권 아이템 / 0·1·2번 슬롯 아이템: `config.yml` 의 `ticket`, `gui.slot-0/1/2`
- 닉네임 변경권은 PersistentDataContainer 태그로 식별되므로, 이름·모델만 비슷한 아이템을 만들어도
  변경권으로 인식되지 않습니다 (위조 방지).

---

## 흔한 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| `Could not find class ... anvilgui` | shade 가 정상적으로 안 됨. `mvn clean package` 재실행. JAR 안에 `com/example/nickname/libs/anvilgui/**` 가 있는지 확인 |
| GUI 가 안 열림 / 콘솔에 NMS 관련 에러 | Paper 버전 확인. **26.1.2** 가 아니면 AnvilGUI fork 가 동작 안 할 수 있음. `lib/anvilgui.jar` 가 Codeberg fork 빌드본인지 확인 |
| 0번 슬롯이 그대로 종이로 보임 | 리소스팩 미적용. 클라이언트가 서버 리소스팩을 수락했는지, `custom-model-data` 값이 일치하는지 확인 |
| 머리 위 닉네임이 아예 안 보임 (위장 / 진짜 모두) | Velocitab 의 `remove_nametags: true` 가 모든 nametag 를 가리고 있음. `false` 로 변경 후 Velocitab 리로드 |
| 머리 위에 진짜 이름만 보이고 위장닉 안 적용 | (1) PacketEvents 플러그인 설치/활성 확인 → `/plugins` 로 확인, (2) `name-tag.enabled: true`, (3) 해당 플레이어가 위장닉을 가졌는지(`/닉네임 확인 <닉>`), (4) 콘솔에 `머리 위 닉네임 기능 활성화` 로그가 떴는지 확인 |
| 위장닉 변경 시 잠시 깜빡임 | 정상. viewer 들에 hide/show 사이클이 1회 돌아야 새 PLAYER_INFO_UPDATE 가 흐르기 때문. 약 50~100ms |
| `PacketEvents 플러그인이 없어` 경고 | PacketEvents 미설치. https://modrinth.com/plugin/packetevents 에서 설치. 머리 위 닉네임이 필요 없으면 `name-tag.enabled: false` 로 두면 경고도 사라짐 |
| 한글 위장닉이 깨져서 보임 | 클라이언트 폰트 문제 가능성. 다른 정상 vanilla 클라이언트에서도 같은 증상이면 PacketEvents 버전 호환성 의심 |
| `%nickname_*%` 가 그대로 출력 | PlaceholderAPI 미설치. `/papi info nickname` 으로 등록 여부 확인 |
| `Could not save messages.yml` 경고 | 최신 버전에서는 발생하지 않음. 만약 보이면 옛 버전이거나 다른 플러그인 이슈 |
| `Duplicate key name 'idx_updated'` 경고 | 최신 버전에서는 발생하지 않음 (기동 시 인덱스 존재 여부를 먼저 확인) |

---

## 라이선스

이 플러그인 자체는 MIT. 셰이딩된 라이브러리들은 각자의 라이선스를 따릅니다.
- AnvilGUI: MIT
- HikariCP: Apache-2.0
- MariaDB Connector/J: LGPL-2.1
