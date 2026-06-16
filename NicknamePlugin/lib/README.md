# lib/ - 수동 설치 라이브러리

이 폴더에는 maven 저장소에서 받을 수 없는 라이브러리의 JAR 파일이 들어갑니다.

## anvilgui.jar (필수)

### 다운로드 방법

**옵션 A: GitHub 소스에서 직접 빌드 (권장)**

```bash
git clone https://github.com/Sytm/AnvilGUI.git
cd AnvilGUI
./gradlew build
# 결과 JAR: build/libs/anvilgui-2.0.0-SNAPSHOT.jar (또는 비슷한 이름)
```

빌드된 JAR 을 이 폴더에 `anvilgui.jar` 라는 이름으로 복사:

```bash
cp build/libs/anvilgui-2.0.0-SNAPSHOT.jar /path/to/NicknamePlugin/lib/anvilgui.jar
```

**옵션 B: GitHub Actions 빌드 산출물에서 다운로드**

1. https://github.com/Sytm/AnvilGUI/actions 접속
2. 최신 성공한 빌드 클릭
3. Artifacts 섹션에서 JAR 다운로드
4. `lib/anvilgui.jar` 로 이름 변경

**옵션 C: md5lukas 의 maven 저장소가 복구되면**

```bash
# 저장소가 살아있는지 확인
curl -I https://repo.md5lukas.de/public/de/md5lukas/anvilgui/2.0.0-SNAPSHOT/

# OK 면 다운로드 (실제 빌드 파일명은 maven-metadata.xml 참고)
curl -o lib/anvilgui.jar https://repo.md5lukas.de/public/de/md5lukas/anvilgui/2.0.0-SNAPSHOT/anvilgui-XXX.jar
```

## 왜 이렇게 해야 하나요?

- 원본 `WesJD/AnvilGUI` 는 NMS 의존성 때문에 Paper 26.x (mojang-mapped) 에서 동작 불가
- `Sytm/AnvilGUI` fork 가 NMS 의존성을 제거해 26.x 호환
- 그러나 Sytm 의 maven 저장소 (`repo.md5lukas.de`) 는 현재 502 Bad Gateway
- JitPack (`com.github.Sytm:AnvilGUI`) 도 401 Unauthorized
- → JAR 을 수동으로 받아 로컬에 두는 방식이 유일한 해결책

## 빌드 후

`lib/anvilgui.jar` 파일이 존재하는 상태에서:

```bash
mvn clean package
```

산출물 `target/NicknamePlugin-1.0.0.jar` 가 AnvilGUI 클래스를 포함한 채로 만들어집니다 (shade plugin 이 자동 처리).
