# MAGO — Metasploit Android GUI Operator

MAGO 是一個 Android 12 以上使用的原生管理介面。Metasploit 本體預計執行於同一台裝置的 Termux 環境；Android App 僅透過固定的 Termux Bridge 與 `127.0.0.1` RPC 介面操作。

> 僅能用於你擁有或已取得明確授權的系統與實驗環境。

## Phase 1 現況

目前完成的是基礎層：

- 多模組 Android 專案
- 可恢復安裝狀態模型
- 固定動作白名單的 Termux Bridge
- Termux 套件、權限和 `RUN_COMMAND` 回呼
- Bridge Bundle SHA-256 驗證
- 僅限 `http://127.0.0.1:55552/api` 的 RPC Policy
- MessagePack RPC 編解碼
- RPC 登入與版本查詢
- Room／Proto DataStore 骨架
- 手機和平板自適應 Onboarding、Dashboard、Diagnostics

Phase 1 **不會安裝 Metasploit**、更新 Termux 套件、初始化 PostgreSQL 或啟動 RPC。這些動作會在後續階段透過固定 Bridge Action 實作。

## Termux 首次授權

Android App 會要求 `Run commands in Termux environment` 權限。Termux 本身還需要由使用者在 Termux 內啟用一次外部命令：

```bash
mkdir -p ~/.termux
printf '%s\n' 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

這一步不能由尚未取得 Termux 執行權限的 App 靜默完成。只應把此權限授予可信任的 App。

## 開發環境

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0
- Android 12（API 31）以上裝置或模擬器
- Termux（不會被封裝進 APK）

此原始碼包沒有附官方 `gradle-wrapper.jar`。Android Studio 可直接同步專案；命令列環境請先安裝 Gradle 9.5.0，或執行：

```bash
gradle wrapper --gradle-version 9.5.0
```

之後可使用：

```bash
./gradlew :app:assembleDebug
```

## 必要驗證

```bash
./gradlew :app:assembleDebug :app:lintDebug \
  :domain:installation:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:termux:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :core:rpc:testDebugUnitTest

bash termux-bridge/tests/contract_test.sh
termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

專案採風險導向測試，不以覆蓋率作為目標。靜態 Compose 排版與簡單 wiring 使用 Build、Lint 和針對性人工 Smoke Test 驗證。

## Phase 2 local installer

MAGO can now drive a fixed Termux Bridge lifecycle for the official Rapid7 Metasploit Framework source, PostgreSQL and a localhost-only MessagePack RPC service. The first installation may take substantial time and storage because native Ruby gems are compiled on-device.

Before starting:

1. Install a compatible Termux build from the official Termux GitHub Releases or F-Droid source.
2. Open Termux once so its bootstrap environment is created.
3. Set `allow-external-apps=true` in `~/.termux/termux.properties`, then run `termux-reload-settings`.
4. Grant MAGO the `com.termux.permission.RUN_COMMAND` permission when Android asks.

The RPC service is bound only to `127.0.0.1:55552`; credentials are generated inside Termux and encrypted with Android Keystore after the one-time Bridge response. MAGO never sends RPC credentials as shell command arguments.
