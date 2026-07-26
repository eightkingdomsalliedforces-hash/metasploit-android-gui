# MAGO Phase 7：發布整備與完整報告預覽設計

日期：2026-07-26  
狀態：待使用者審閱  
基準分支：`main`

## 1. 背景

MAGO 的主線已完成 Termux／Metasploit 安裝、localhost MessagePack RPC、模組瀏覽與受控單項操作、唯讀 Jobs／Sessions 摘要、Database Inventory、Workspace 管理，以及 JSON／CSV／HTML／ZIP 安全報告匯出。

目前存在四個整備問題：

1. README 仍停留在 Phase 2，與主線功能不同步。
2. 報告頁只能選格式並直接匯出，無法在 App 內檢查本次資料。
3. GitHub Actions 已能建置及上傳 Debug APK，但使用者文件未清楚說明取得方式與驗證方式。
4. 舊的 Phase 3／Phase 4 PR 仍開啟，且已與主線分歧，容易被誤認為目前開發基準或誤合併。

Phase 7 的目標是讓專案可理解、可驗證、可取得 Debug APK，並新增完整但受控的本機報告預覽；本階段不擴張 Metasploit 的攻擊能力。

## 2. 已確認決策

- 發布產物只做 GitHub Actions 的 Debug APK，不加入 Release keystore 或正式簽章流程。
- 報告頁採完整資料預覽，不只顯示數量摘要。
- 預覽可顯示現有 Inventory 模型中的已知原始欄位。
- `extraFields` 在預覽中可顯示原始鍵值，但敏感鍵名遞迴遮罩。
- 原始預覽資料只存在 App 記憶體，不加入 JSON／CSV／HTML／ZIP。
- 匯出格式繼續沿用現有白名單與敏感參數遮罩。
- Phase 7 必須建立在最新 `main`，不得建立在已分歧的 Phase 4 PR #12 上。

## 3. 目標

### 3.1 功能目標

1. 報告頁載入同一份一致性快照，供完整預覽及安全匯出共用。
2. 手機使用可展開卡片，平板使用列表＋詳情雙欄。
3. 預覽 Hosts、Services、Vulnerabilities 與本機模組執行紀錄，各最多 100 筆。
4. 顯示 Workspace、快照時間、資料筆數、截斷提示及重新整理狀態。
5. 安全匯出不重新呼叫 RPC，使用目前預覽對應的同一份快照。
6. 更新 README、功能矩陣、安全邊界、開發驗證及 Debug APK 下載說明。
7. 在診斷頁加入不含秘密的版本與建置資訊。
8. 對已被主線取代的舊 PR 留下說明後關閉，避免誤合併。

### 3.2 品質目標

- 任一資料來源失敗時不建立混合新舊資料的快照。
- `extraFields` 不寫入檔案、Room、Logcat、錯誤訊息或 CI artifact。
- 大型／深層 RPC 結構不能造成無限遞迴、記憶體暴增或 UI 長時間阻塞。
- 不新增背景輪詢、自動重試、廣泛儲存權限或外部網路端點。

## 4. 非目標

本階段不做：

- Release APK、AAB、Play Store 發布或簽章密鑰管理。
- Session 自動互動、批次命令、自動後滲透、自動掃描或攻擊鏈。
- Credentials RPC、Loot 匯出、RPC 密碼／Token／Keystore 顯示。
- 把原始 `info`、`comments`、`extraFields` 加入任何可分享報告。
- 背景同步、週期重新整理或離線持久化原始預覽。
- 全面重做 App 導覽或視覺語言。

## 5. 方案比較

### 5.1 方案 A：單一記憶體快照（採用）

重新整理時一次讀取 Workspace、Hosts、Services、Vulnerabilities 與執行紀錄，形成 `ReportPreviewSnapshot`。畫面直接顯示此快照；匯出時把同一快照轉換成現有安全 `ReportSnapshot`。

優點：

- 預覽與匯出屬於同一時間點。
- 原始預覽與安全匯出可用不同型別隔離。
- 切換格式不重新呼叫 RPC。
- 易於測試資料來源呼叫次數與失敗關閉。

### 5.2 方案 B：跳轉既有 Inventory 頁

報告頁連到 Inventory，再返回匯出。此方式會讓預覽與匯出各自呼叫 RPC，且無法自然包含本機模組執行紀錄，因此不採用。

### 5.3 方案 C：以 HTML／WebView 預覽

先產生 HTML 再顯示。現有 HTML 是安全白名單，無法滿足完整原始預覽；若放寬 HTML，又會擴大可分享文件與 WebView 的資料外洩面，因此不採用。

## 6. 架構

```text
ReportsViewModel
    │
    ├── ensurePreviewLoaded()
    ├── refreshPreview()
    │     ├── inventoryRepository.currentWorkspace()
    │     ├── inventoryRepository.hosts(limit = 100)
    │     ├── inventoryRepository.services(limit = 100)
    │     ├── inventoryRepository.vulnerabilities(limit = 100)
    │     └── moduleLocalStore.executionHistory(limit = 100)
    │
    └── ReportsUiState.previewSnapshot
              │
              ├── ReportsScreen：完整本機預覽
              │
              └── requestExport()
                     └── PreviewSnapshot.toSafeReportSnapshot()
                             └── 現有 ReportDocumentBuilder
                                  └── JSON／CSV／HTML／ZIP
```

### 6.1 模組邊界

- `feature:reports`
  - 擁有預覽狀態、選取狀態、載入流程與 Compose UI。
  - 不直接解析 MessagePack。
- `core:model`
  - 繼續提供 Inventory 與 `RpcValue` 型別。
- `core:reporting`
  - 保持安全匯出模型與文件建構器。
  - 可加入純函式轉換／遮罩輔助，但不得依賴 Compose。
- `domain:metasploit`
  - 沿用現有 repository 介面，不新增 Credentials 或其他高風險 RPC。
- `app`
  - 只做 ViewModel wiring、生命週期觸發與 SAF launcher，不放入預覽渲染邏輯。

## 7. 資料模型

### 7.1 `ReportPreviewSnapshot`

建議放在 `feature:reports` 或一個僅供報告功能使用的 model 檔案：

```kotlin
data class ReportPreviewSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)
```

此型別可包含 `info`、`comments`、時間及 `extraFields`，但不得序列化或持久化。

### 7.2 安全匯出模型

現有 `ReportSnapshot` 繼續是匯出白名單。新增明確轉換：

```kotlin
fun ReportPreviewSnapshot.toSafeReportSnapshot(): ReportSnapshot
```

轉換只複製現有 `ReportSnapshot` 支援的欄位。不得把 `info`、`comments` 或 `extraFields` 傳給文件建構器。

### 7.3 UI 狀態

`ReportsUiState` 至少包含：

- `format`
- `initialLoading`
- `refreshing`
- `previewSnapshot`
- `selectedTab`
- 各類型目前選取項目的穩定識別鍵
- `pendingDocument`
- `refreshErrorMessage`
- `exportErrorMessage`
- `saveMessage`

初次載入與重新整理使用不同狀態，讓重新整理時可保留舊快照。

## 8. 資料載入與一致性

### 8.1 首次載入

進入報告 destination 時呼叫 `ensurePreviewLoaded()`：

- 已有快照：不呼叫資料來源。
- 尚無快照：執行一次完整載入。
- 不因 recomposition 重複載入。

### 8.2 手動重新整理

`refreshPreview()` 按固定順序讀取：

1. 作用中 Workspace
2. Hosts
3. Services
4. Vulnerabilities
5. 本機執行紀錄

只有五個來源全部成功，才一次替換 `previewSnapshot`。任一來源失敗：

- 保留舊快照。
- 顯示「重新整理失敗，畫面仍為先前快照」。
- 不產生部分新快照。
- 不自動重試。

### 8.3 匯出

- 無快照時停用匯出。
- 匯出只讀取記憶體中的 `previewSnapshot`。
- 匯出期間不呼叫 Inventory repository 或本機 store。
- 文件建立失敗時不開啟 SAF。
- SAF 取消不視為錯誤。

## 9. 完整預覽介面

### 9.1 頂部區域

顯示：

- 「報告」標題。
- 原始資料警告卡。
- 作用中 Workspace 名稱與 ID。
- 快照時間。
- Hosts／Services／Vulnerabilities／執行紀錄數量。
- 每類最多 100 筆提示。
- 「重新整理」按鈕與載入進度。

警告文字必須明確說明：

> 此預覽可能包含資產備註、服務資訊及 Metasploit 回傳的未知欄位。資料只保存在目前 App 記憶體中；安全匯出不包含這些原始欄位。

### 9.2 類型切換

使用四個 `FilterChip`：

- Hosts
- Services
- Vulnerabilities
- 執行紀錄

切換類型時清除不相容的選取項目。

### 9.3 手機

- 使用 `LazyColumn`，不得用一般 `Column` 一次組合所有記錄。
- 每筆為可展開 Card。
- 摘要顯示識別欄位；展開後顯示全部已知欄位與 `extraFields`。
- 同一時間可允許一筆或多筆展開；實作計畫優先選擇單筆展開以降低狀態複雜度。

### 9.4 平板

600dp 以上使用列表＋詳情雙欄：

- 左欄：LazyColumn 記錄列表。
- 右欄：選取記錄的完整欄位。
- 未選取時顯示提示。
- 右欄可獨立捲動。

## 10. 欄位呈現

### 10.1 Workspace

- ID
- Name
- Created At
- Updated At
- `extraFields`

### 10.2 Host

- Address
- MAC
- Name
- State
- Operating System
- Operating System Flavor
- Service Pack
- Language
- Purpose
- Info
- Comments
- Created At
- Updated At
- `extraFields`

### 10.3 Service

- Host
- Port
- Protocol
- State
- Name
- Info
- Created At
- Updated At
- `extraFields`

### 10.4 Vulnerability

- Host
- Port
- Protocol
- Name
- References
- Resource
- Reported At
- `extraFields`

### 10.5 Module execution

只顯示 Room 已保存的執行紀錄。選項值沿用持久化前既有遮罩，不能嘗試還原秘密。

## 11. `RpcValue` 渲染與防護

新增純函式 renderer，把 `RpcValue` 轉成可預測的顯示節點或字串。規則：

- `Nil` → `null`
- `Bool`／`IntValue`／`FloatValue` → 原始值
- `StringValue` → 保留換行的文字
- `ArrayValue` → 有序、縮排陣列
- `MapValue` → 鍵名排序後的縮排 Map
- `BinaryValue` → 位元組數與十六進位預覽，不嘗試 UTF-8 解碼

### 11.1 敏感鍵名遮罩

對 Map／`extraFields` 遞迴檢查鍵名。鍵名正規化為小寫並移除 `_`、`-`、空白後，符合下列語意者把整個值顯示為 `[REDACTED]`：

- password／passwd／passphrase
- token／accessToken／refreshToken
- credential／credentials
- secret／clientSecret
- apiKey
- privateKey
- auth／authorization（值可能是認證資料時）

此拒絕清單只降低意外外洩風險，不能宣稱可識別所有秘密；因此 UI 仍必須保留原始資料警告。

### 11.2 渲染上限

- 最大遞迴深度：8
- 每個 Array／Map 最多渲染 500 個元素
- 單一 String 最多渲染 64 KiB
- 單一 Binary 最多顯示前 4 KiB 的十六進位內容
- 超限時顯示明確截斷標記
- 上限只影響畫面渲染，不修改 repository 回傳模型

### 11.3 禁止輸出位置

原始值不得出現在：

- Logcat
- exception message
- Analytics／Telemetry
- Room
- SavedStateHandle
- JSON／CSV／HTML／ZIP
- GitHub Actions 診斷 artifact

離開 ViewModel 生命週期後移除快照引用；由於 JVM 無法保證記憶體立即清零，文件不得宣稱已安全抹除位元組。

## 12. 匯出區域

完整預覽下方保留格式選擇：

- JSON
- CSV
- HTML
- ZIP

主按鈕改為：

> 產生安全版報告並選擇儲存位置

旁邊列出安全匯出仍排除：

- RPC 密碼、Token、Credentials、Keystore
- Console 輸入輸出
- `info`、`comments`、`extraFields`
- 模組結果與原始錯誤內容
- 未在 `ReportSnapshot` 白名單中的欄位

## 13. 診斷與版本資訊

在既有 Diagnostics 頁加入「版本資訊」區塊：

- App `versionName`／`versionCode`
- Build type（Debug）
- Android API level
- Bridge bundle version
- Bridge bundle SHA-256
- 安裝階段／健康狀態
- 固定 RPC endpoint 描述（只顯示 `127.0.0.1`，不顯示帳密）

不得加入 Android ID、廣告 ID、序號、RPC 密碼或完整私有檔案路徑。

## 14. GitHub Actions 與 Debug APK

現有 `.github/workflows/android.yml` 已執行：

- Termux Bridge contract test
- shell syntax 驗證
- embedded bundle 一致性驗證
- `:app:assembleDebug`
- Lint
- 風險導向 unit tests
- 上傳 `app-debug.apk`

Phase 7 不重建 CI 架構。只做必要整備：

1. 保持 PR、push to main、workflow_dispatch 觸發。
2. 保持 Debug APK artifact 上傳失敗即 workflow 失敗。
3. 可新增 APK SHA-256 文字檔並與 APK 放在同一 artifact；若實作增加不必要複雜度可省略。
4. README 說明從 Actions 頁下載 artifact，並說明 artifact 是 Debug build、不是正式簽署版本。
5. README 不提供繞過 Android 安裝警告或安全限制的方式。

## 15. 文件同步

README 改寫為目前主線狀態，至少包含：

- 專案定位與合法授權警告
- 已完成能力矩陣（Phase 2 至 Phase 7）
- Android／Termux／JDK／SDK 需求
- 安裝與首次 Termux 授權
- 資料位置與 localhost RPC 邊界
- 功能導覽
- 報告預覽與安全匯出差異
- GitHub Actions 驗證命令
- Debug APK artifact 下載步驟
- 已知限制
- 不支援／不計畫提供的高風險自動化

## 16. 舊 PR 整理

### 16.1 PR #12

`feature/phase4-jobs-sessions` 已從舊基準分歧，不能直接合併。處理方式：

1. 留下關閉說明，指出主線已加入不同的 Phase 4 安全實作並進入 Phase 5／6。
2. 說明若未來要做 Session 互動，必須從最新 `main` 重新設計，僅移植仍有效的測試與安全限制。
3. 關閉 PR，不刪除分支，以保留歷史參考。

### 16.2 其他已被取代的開啟 PR

以相同原則檢查 PR #9 等舊分支：若主線已包含同等或更完整實作，留下取代說明後關閉；不得未審查就批量合併或刪除分支。

## 17. 錯誤處理

### 17.1 初次載入失敗

- 顯示錯誤狀態與手動重試。
- 不顯示空白快照為成功。
- 匯出按鈕停用。

### 17.2 重新整理失敗

- 保留舊快照。
- 顯示失敗訊息及舊快照時間。
- 不更新任何一部分資料。

### 17.3 Renderer 失敗

`RpcValue` renderer 應為總函式；已知 sealed subtype 都有處理。遇到防護上限時顯示截斷，不拋出原始值到錯誤訊息。

### 17.4 匯出失敗

- Snapshot 轉換或文件建立失敗：不開 SAF。
- SAF 寫入失敗：顯示一般化訊息，不包含目的 URI、私有路徑或報告內容。
- SAF 取消：顯示已取消，不視為錯誤。

## 18. 測試策略

### 18.1 ViewModel 測試

- 初次載入每個來源只呼叫一次。
- 已有快照時 `ensurePreviewLoaded()` 零呼叫。
- 完整成功才原子替換快照。
- 任一來源失敗保留舊快照。
- 無快照時匯出 fail-closed。
- 匯出不重新呼叫 repository／store。
- 匯出使用當前快照與所選格式。
- 重複點擊載入／匯出不產生並行重複操作。

### 18.2 安全轉換測試

建立含秘密與原始欄位的 `ReportPreviewSnapshot`，斷言所有格式均不含：

- `info`
- `comments`
- `extraFields` 的鍵和值
- 密碼、Token、Credential 測試字串
- Console、結果與錯誤內容測試字串

### 18.3 `RpcValue` renderer 測試

- 所有 subtype。
- Map 鍵排序。
- 遞迴敏感鍵名遮罩。
- 最大深度、元素數、字串與 Binary 截斷。
- Binary 不被當成 UTF-8 直接顯示。
- renderer 不修改輸入模型。

### 18.4 UI 驗證

- Build／Lint 驗證 Compose wiring。
- 人工 smoke test：手機單欄、平板雙欄、空列表、100 筆列表、長文字、深層 Map、重新整理失敗、SAF 取消。
- 不為純靜態排版增加脆弱的像素測試。

### 18.5 完整 CI

沿用現有 workflow，將新增的 `feature:reports`／`core:reporting` 測試納入既有 Gradle 命令。CI 成功後必須存在 Debug APK artifact。

## 19. 實作切片

為降低衝突，Phase 7 分為四個可獨立審查的切片：

1. **7A：預覽資料與安全隔離**
   - `ReportPreviewSnapshot`
   - ViewModel 原子載入
   - Preview → Safe Snapshot
   - 安全及狀態測試
2. **7B：完整自適應預覽 UI**
   - 手機展開卡
   - 平板雙欄
   - `RpcValue` renderer 與防護
3. **7C：文件、診斷與 Debug APK 說明**
   - README
   - 版本資訊
   - 視需要加入 APK checksum
4. **7D：舊 PR 整理**
   - 留下取代說明
   - 關閉已分歧且不可直接合併的舊 PR

每個切片從當時最新 `main` 建立乾淨分支；不在 PR #12 分支上堆疊。

## 20. 驗收條件

Phase 7 完成時：

- 報告頁能在手機和平板完整預覽四類資料。
- 已知原始欄位可查看。
- `extraFields` 可遞迴查看，但敏感鍵名值顯示 `[REDACTED]`。
- 原始預覽不被持久化、記錄或匯出。
- 匯出使用同一份快照且繼續通過秘密排除測試。
- 重新整理失敗不破壞舊快照。
- README 與主線功能一致。
- 使用者能依 README 從 GitHub Actions 取得 Debug APK。
- Diagnostics 顯示不含秘密的版本／Bridge 資訊。
- 舊 Phase 4 PR 不再被誤認為可直接合併的目前工作。
- Android Build、Lint、Bridge contract tests、風險導向 unit tests 全部通過，CI 上傳 Debug APK。

## 21. 安全結論

Phase 7 增加的是本機可見性、發布可驗證性及專案整潔度，不增加自主攻擊能力。完整預覽刻意與可分享匯出分離：原始欄位只在當前 ViewModel 記憶體中呈現；安全報告繼續由明確白名單產生。