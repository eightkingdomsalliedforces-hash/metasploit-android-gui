# MAGO — Metasploit Android GUI Operator

MAGO 是供 Android 12 以上裝置使用的原生管理介面。Metasploit、PostgreSQL 與 MessagePack RPC 執行於同一台裝置的 Termux 私有環境；Android App 只透過固定 Bridge 動作與 `127.0.0.1` RPC 操作。

> 僅能用於你擁有或已取得明確授權的系統與隔離實驗環境。

## Phase 2 現況

目前已完成：

- 可恢復的 Termux 環境檢查與 Bridge v2 部署
- Termux 套件更新與固定相依套件安裝
- 從 Rapid7 官方 Git 倉庫安裝、修復及更新 Metasploit Framework
- PostgreSQL 私有叢集與 Metasploit database schema 初始化
- 只綁定 `127.0.0.1:55552` 的 MessagePack RPC
- RPC 帳密以環境變數傳給現行 `msfrpcd`，不放入程序命令列
- RPC 密碼以 Android Keystore AES/GCM 保存
- PostgreSQL／RPC 啟動、停止與精確健康檢查
- 中斷後依健康狀態跳過已完成階段
- 手機與平板安裝進度、診斷與重試介面

首次完整安裝會下載原始碼並編譯 Ruby native gems，實際時間和儲存空間取決於裝置、網路及 Termux 套件版本。App 不會偽造進度或隱藏 Android 系統確認畫面。

## Termux 安裝與首次授權

目前 APK 會偵測 Termux；尚未安裝時會要求使用者從 Termux 官方 GitHub Releases 或 F-Droid 安裝。不同來源的 Termux 與外掛使用不同簽章，不能混裝。

Termux 第一次開啟完成 bootstrap 後，還需要由使用者在 Termux 內啟用一次外部命令：

```bash
mkdir -p ~/.termux
grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || \
  printf '%s\n' 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

接著回到 MAGO，授予 `Run commands in Termux environment` 權限並按「重試」。這些確認不能在未 Root Android 上靜默跳過。

## 安裝資料位置

Bridge 管理的資料位於 Termux HOME：

```text
~/.mago/metasploit-framework
~/.mago/bundle
~/.mago/postgresql
~/.mago/config/rpc.env
~/.mago/logs
```

Metasploit database 設定寫入 `~/.msf4/database.yml`。RPC 僅允許 `http://127.0.0.1:55552/api`。

## 開發環境

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0
- Android 12（API 31）以上裝置或模擬器
- Termux `v0.118.0` 以上

命令列驗證：

```bash
gradle --no-daemon :app:assembleDebug :app:lintDebug \
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

專案採風險導向測試，不以覆蓋率為目標。靜態 Compose 排版與簡單 wiring 使用 Build、Lint 和人工 Smoke Test 驗證。
