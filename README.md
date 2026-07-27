# MAGO — Metasploit Android GUI Operator

MAGO 是供 Android 12 以上裝置使用的原生管理介面。Metasploit、PostgreSQL 與 MessagePack RPC 執行於同一台裝置的 Termux 私有環境；Android App 只透過固定 Bridge 動作與 `127.0.0.1` RPC 操作。

> 僅能用於你擁有、已取得明確授權的系統，或隔離實驗環境。

## 目前能力

### Termux 與 Metasploit 環境

- 可恢復的 Termux 環境檢查與版本化 Bridge 部署
- Termux 套件更新與固定相依套件安裝
- 從 Rapid7 官方 Git 倉庫安裝、修復及更新 Metasploit Framework
- PostgreSQL 私有叢集與 Metasploit database schema 初始化
- 只綁定 `127.0.0.1:55552` 的 MessagePack RPC
- RPC 帳密以環境變數傳給現行 `msfrpcd`，不放入程序命令列
- RPC 密碼以 Android Keystore AES/GCM 保存
- PostgreSQL／RPC 啟動、停止、健康檢查、Metasploit 更新與快取清理
- 中斷後依健康狀態跳過已完成階段，不偽造進度或靜默重試

### 模組與操作

- 依類型瀏覽及搜尋 Metasploit 模組
- 模組資訊、選項、收藏與最近使用紀錄
- RPC 不可用時使用本機唯讀快取
- Check／Execute 前要求明確確認及授權用途聲明
- 執行前稽核 fail-closed；稽核寫入失敗時不呼叫 RPC
- 只保存已遮罩的參數、狀態、Job ID／UUID 與必要稽核資訊
- 首頁提供 Jobs／Sessions 摘要與手動重新整理
- 可在二次確認後停止單一 Job 或單一 Session
- 每次確認最多送出一次 `job.stop` 或 `session.stop`，不自動重試
- 停止成功後只執行一次 Jobs／Sessions 原子重新讀取；任一讀取失敗會保留舊快照
- Metasploit Console 操作保持在既有受控 RPC／記憶體邊界內

MAGO 不提供 Session 命令、批量停止、全部停止、自動後滲透、自主掃描、憑證擷取捷徑、背景 Session 輪詢或自動停止重試。

### Workspace、資產與報告

- 查看及切換作用中 Workspace
- 明確建立 Workspace；不提供刪除 Workspace
- 唯讀查看 Hosts、Services 與 Vulnerabilities，每類最多 100 筆
- 報告快照一次讀取 Workspace、資產及本機執行紀錄；任一來源失敗即不建立新舊混合快照
- 重新整理失敗時保留上一份完整快照
- App 內完整預覽已知原始欄位與 Metasploit `extraFields`
- 原始預覽可包含 MAC、資產備註、服務資訊、時間欄位及本機模組結果／錯誤，但只存在目前 ViewModel／UI 記憶體
- 原始巢狀值有固定顯示上限：深度 8、每個容器 500 項、字串 65,536 字元、Binary 4,096 bytes
- JSON、CSV、HTML 與 ZIP 匯出固定走安全白名單及再次遮罩
- 匯出排除 RPC 密碼、Token、Credentials、Keystore、Console、完整路徑、資產自由文字、`extraFields`、原始結果與原始錯誤
- 報告使用 Android Storage Access Framework，不要求廣泛儲存權限

### App 安全、顯示與診斷

- 可選的 App 鎖，使用 Android 系統生物辨識或裝置 PIN／圖形
- 啟用、停用及解鎖均由系統驗證，不保存生物特徵或裝置憑證
- App 離開前景後重新鎖定，並以 `FLAG_SECURE` 保護一般截圖及最近使用預覽
- 跟隨系統、淺色、深色與 AMOLED 主題
- 100%、130%、160% 與 200% 字體級距
- 可持久化的減少動畫設定
- 手機與平板自適應導覽及大字體捲動處理
- 診斷頁顯示 MAGO 版本、Bridge bundle 版本／SHA、Android API、CPU ABI、Metasploit 版本及安全安裝狀態
- 使用者可手動複製固定格式、嚴格白名單的已遮罩診斷摘要
- 診斷摘要不包含品牌、型號、裝置識別碼、帳密、Token、完整路徑、原始錯誤或未知 Bridge 欄位
- MAGO 不會自動上傳、背景傳送或持久化診斷摘要

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

首次完整安裝會下載原始碼並編譯 Ruby native gems，實際時間和儲存空間取決於裝置、網路及 Termux 套件版本。

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

## 建置產物

### Debug APK

`.github/workflows/android.yml` 會在 Pull Request、推送至 `main`，或手動執行時：

- 驗證 Termux Bridge 合約、Shell 語法、內嵌 SHA 與可重現 bundle
- 執行 Debug Build、Lint 及風險導向單元測試
- 上傳 `mago-debug-apk-<run number>`，保留 14 天

Debug APK 用於開發與真機驗收，不是正式商店發布版本。

### Unsigned Release APK／AAB

倉庫另有手動 unsigned Release workflow，可建立明確標示為未簽章的 APK、AAB、建置資訊與 SHA-256 清單。

這些產物：

- 不包含發布簽章金鑰
- 不會自動建立 GitHub Release
- 不會自動上傳 Google Play
- 不能視為可直接對外發布的正式簽章版本

## 開發環境

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0
- Android 12（API 31）以上裝置或模擬器
- Termux `v0.118.0` 以上

## 命令列驗證

```bash
gradle --no-daemon --stacktrace \
  :app:assembleDebug \
  :app:lintDebug \
  :app:testDebugUnitTest \
  :domain:installation:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:termux:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :core:rpc:testDebugUnitTest \
  :core:reporting:testDebugUnitTest \
  :feature:modules:testDebugUnitTest \
  :feature:dashboard:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :feature:diagnostics:testDebugUnitTest

python3 -m pip install --disable-pip-version-check check-jsonschema
bash termux-bridge/tests/contract_test.sh
bash -n \
  termux-bridge/scripts/dispatch.sh \
  termux-bridge/scripts/lib/common.sh \
  termux-bridge/scripts/actions/*.sh

termux-bridge/packaging/build_bundle.sh
git diff --exit-code -- \
  termux-bridge/scripts \
  termux-bridge/packaging/build_bundle.sh \
  core/termux/src/main/res/raw/mago_bridge_v1.tgz \
  core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt
```

專案採風險導向測試，不以覆蓋率為目標。靜態 Compose 排版與簡單 wiring 使用 Build、Lint 和人工 Smoke Test 驗證。

## 真機 Smoke Test

發布候選版本至少應在 Android 12 以上真機確認：

- [ ] Termux 首次啟動、`allow-external-apps` 與 Android 權限流程
- [ ] Bridge 首次部署、中斷恢復與既有階段跳過
- [ ] PostgreSQL／RPC 啟動、停止及健康檢查
- [ ] Workspace 建立、切換及 Hosts／Services／Vulnerabilities 讀取
- [ ] 模組搜尋、收藏、離線快取、Check／Execute 確認及稽核失敗關閉
- [ ] Jobs／Sessions 摘要與手動重新整理
- [ ] 取消停止確認時不送出 RPC
- [ ] Job 停止成功後只自動驗證刷新一次
- [ ] Session 停止成功後只自動驗證刷新一次
- [ ] 快速連點只產生一個停止 RPC
- [ ] 停止期間刷新、詳情、其他停止與維護控制不可用
- [ ] RPC 離線時不顯示誤導性的停止成功
- [ ] 160%／200% 字體下確認內容與按鈕完整可用
- [ ] reduced-motion 下停止過程只使用靜態文字
- [ ] App 鎖啟用、停用、背景返回及系統驗證取消
- [ ] 淺色、深色、AMOLED、100%～200% 字體與減少動畫
- [ ] 原始報告欄位展開、巢狀 `extraFields` 及截斷提示
- [ ] 預覽重新整理失敗時保留上一份完整快照
- [ ] JSON、CSV、HTML、ZIP 四種 SAF 儲存及取消流程
- [ ] 匯出文件中不存在原始預覽專用欄位與測試秘密值
- [ ] 診斷頁顯示 App、Bridge、Android／ABI、Metasploit 與安全安裝狀態
- [ ] Bridge SHA 在畫面顯示短碼，複製摘要包含完整 SHA
- [ ] 敏感、未知與 deny-list Bridge 欄位不顯示原值且不進入摘要
- [ ] RPC host 只顯示 localhost 是／否，不顯示原始位址
- [ ] 剪貼簿成功時顯示「已複製診斷摘要」
- [ ] 模擬剪貼簿失敗時只顯示「無法複製診斷摘要」
- [ ] 複製內容不含品牌、型號、裝置識別碼、帳密、Token、完整路徑或原始錯誤
