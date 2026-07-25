# MAGO Phase 2 Installer and Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 12+ 未 Root 裝置上，透過 Termux Bridge 完成 Metasploit、PostgreSQL 與本機 MessagePack RPC 的可恢復安裝、啟動、停止、修復及健康檢查。

**Architecture:** Android App 只呼叫固定 `BridgeAction`；Bridge v2 以獨立 Shell action 管理套件、官方 Rapid7 原始碼、`msfdb` 與 `msfrpcd`。RPC 密碼由 Bridge 產生，透過一次性命令結果回傳後存入 Android Keystore；服務只綁定 `127.0.0.1`。安裝協調器依既有 `InstallationStage` 逐階段持久化，可在 App 重建後重新檢查並安全跳過已完成工作。

**Tech Stack:** Kotlin 2.4.10、AGP 9.3.0、Jetpack Compose、Android Keystore AES/GCM、Kotlin Serialization、Bash、Termux `pkg`、Ruby Bundler、PostgreSQL、Rapid7 Metasploit Framework MessagePack RPC。

## Global Constraints

- `minSdk = 31`，即 Android 12。
- `compileSdk = 36`、`targetSdk = 36`。
- RPC 只允許 `127.0.0.1:55552/api`。
- 不要求 Root、Shizuku、雲端或外部電腦。
- Android App 不得拼接任意 Shell；只能呼叫固定 Bridge 動作。
- RPC 密碼不得出現在命令列參數、一般日誌或 Room。
- Metasploit 來源固定為 `https://github.com/rapid7/metasploit-framework.git`。
- 測試採風險導向：只測 Bridge 冪等性、安全輸入、明確失敗碼、Keystore round-trip 與安裝流程轉換。

---

### Task 1: Bridge v2 Actions and Shared Runtime

**Files:**
- Modify: `core/model/src/main/kotlin/dev/mago/android/model/bridge/BridgeAction.kt`
- Modify: `termux-bridge/schemas/request.schema.json`
- Modify: `termux-bridge/schemas/response.schema.json`
- Create: `termux-bridge/scripts/lib/common.sh`
- Modify: `termux-bridge/scripts/dispatch.sh`

**Interfaces:**
- Produces fixed actions `UPDATE_PACKAGES`, `INSTALL_DEPENDENCIES`, `CONFIGURE_RPC` plus existing lifecycle actions.
- Produces `bridge_ok`, `bridge_fail`, `with_install_lock`, `process_running` helpers.

- [ ] Add the three missing stage actions to Kotlin and both schemas.
- [ ] Add shared JSON escaping, state paths, lock acquisition and process helpers.
- [ ] Dispatch every allowlisted action to a fixed script path; reject unknown action and invalid operation ID.
- [ ] Run `bash termux-bridge/tests/contract_test.sh` and confirm unknown actions still return exit code `64`.

### Task 2: Package and Metasploit Installation Scripts

**Files:**
- Create: `termux-bridge/scripts/actions/update-packages.sh`
- Create: `termux-bridge/scripts/actions/install-dependencies.sh`
- Create: `termux-bridge/scripts/actions/install-metasploit.sh`
- Create: `termux-bridge/scripts/actions/repair-metasploit.sh`
- Create: `termux-bridge/scripts/actions/update-metasploit.sh`

**Interfaces:**
- `INSTALL_METASPLOIT` creates `$HOME/.mago/metasploit-framework` from the official Rapid7 repository.
- All scripts are idempotent and return structured Bridge responses.

- [ ] Update Termux package indexes with non-interactive `pkg`.
- [ ] Install the fixed dependency allowlist required by Ruby, PostgreSQL and native gems.
- [ ] Clone or fast-forward the official Rapid7 repository, install the Bundler version from `Gemfile.lock`, and run production `bundle install`.
- [ ] Verify `msfconsole`, `msfrpcd` and `msfdb` before reporting success.
- [ ] Add repair and update actions without destructive database deletion.

### Task 3: PostgreSQL and RPC Lifecycle

**Files:**
- Create: `termux-bridge/scripts/actions/initialize-database.sh`
- Create: `termux-bridge/scripts/actions/configure-rpc.sh`
- Create: `termux-bridge/scripts/actions/start-services.sh`
- Create: `termux-bridge/scripts/actions/stop-services.sh`
- Create: `termux-bridge/scripts/actions/start-rpc.sh`
- Create: `termux-bridge/scripts/actions/stop-rpc.sh`
- Modify: `termux-bridge/scripts/actions/health-check.sh`

**Interfaces:**
- `CONFIGURE_RPC` returns `rpcUser`, `rpcPassword`, and `credentialsCreated`.
- `START_RPC` launches `msfrpcd` with credentials in environment variables, bound to `127.0.0.1:55552`, SSL disabled only because transport is localhost-only.

- [ ] Initialize the Metasploit database with `msfdb --component database --use-defaults init` only when configuration is absent.
- [ ] Generate 32-byte RPC credentials with `openssl rand`, store them at mode `600`, and never pass them as process arguments.
- [ ] Start/stop PostgreSQL and RPC using PID/state files under `$HOME/.mago`.
- [ ] Extend health data with database, repository, RPC process and exact localhost port status.
- [ ] Map package, database and RPC failures to distinct Bridge exit codes.

### Task 4: Reproducible Bridge Bundle and Contract Tests

**Files:**
- Modify: `termux-bridge/packaging/build_bundle.sh`
- Modify: `termux-bridge/tests/contract_test.sh`
- Create: `termux-bridge/tests/fakes/pkg`
- Create: `termux-bridge/tests/fakes/git`
- Create: `termux-bridge/tests/fakes/bundle`
- Create: `termux-bridge/tests/fakes/msfdb`
- Create: `termux-bridge/tests/fakes/msfrpcd`

**Interfaces:**
- Embedded bundle contains all Bridge v2 scripts with deterministic metadata and SHA-256.

- [ ] Include `lib/common.sh` and every allowlisted action in the deterministic archive.
- [ ] Use a temporary HOME/PREFIX and fake commands to prove repeated install and configuration are idempotent.
- [ ] Verify RPC password never appears in the command line or test logs.
- [ ] Rebuild the bundle and update `BridgeBundleMetadata` digest and version.

### Task 5: Android Keystore Secret Store

**Files:**
- Create: `core/security/src/main/kotlin/dev/mago/android/security/AndroidKeystoreSecretStore.kt`
- Create: `core/security/src/test/kotlin/dev/mago/android/security/RpcSecretCodecTest.kt`
- Modify: `app/src/main/kotlin/dev/mago/android/AppContainer.kt`

**Interfaces:**
- Implements existing `SecretStore` using AndroidKeyStore AES/GCM and private SharedPreferences ciphertext.

- [ ] Encode encrypted values as versioned IV+ciphertext records.
- [ ] Clear input `CharArray` after save attempts.
- [ ] Replace `UnconfiguredSecretStore` in `AppContainer`.
- [ ] Keep only pure record-format tests; AndroidKeyStore behavior is covered by one instrumentation smoke test later.

### Task 6: Full Recoverable Bootstrap Coordinator

**Files:**
- Modify: `domain/installation/src/main/kotlin/dev/mago/android/installation/BootstrapCoordinatorImpl.kt`
- Modify: `domain/installation/src/test/kotlin/dev/mago/android/installation/BootstrapCoordinatorTest.kt`

**Interfaces:**
- Sequentially runs Bridge actions for every installation stage and stores returned RPC credentials before login.

- [ ] Inspect health first and skip already satisfied stages.
- [ ] Run update, dependency, install, database, credential and service actions in stage order.
- [ ] Persist each stage before the operation and after success/failure.
- [ ] Store RPC credentials, clear the response password copy, then call RPC health/version.
- [ ] Classify database and RPC errors into existing `InstallationFailureKind` values.
- [ ] Add only coordinator tests for resume, database failure and RPC credential storage.

### Task 7: Onboarding and Diagnostics Integration

**Files:**
- Modify: `feature/onboarding/src/main/kotlin/dev/mago/android/onboarding/OnboardingScreen.kt`
- Modify: `core/ui/src/main/kotlin/dev/mago/android/ui/components/InstallationStepper.kt`
- Modify: `README.md`

**Interfaces:**
- Shows all Phase 2 stages and warns that Android will keep Termux installation/user confirmation visible.

- [ ] Remove Phase 1-only wording.
- [ ] Display package, Metasploit, database, RPC and verification stages.
- [ ] Document expected storage/time, Termux `allow-external-apps=true`, and recovery actions.
- [ ] Do not add screenshot tests for static UI.

### Task 8: CI Verification and APK Artifact

**Files:**
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- CI runs Bridge contract tests, deterministic bundle check, affected unit tests, Lint and debug APK.

- [ ] Add Phase 2 shell tests to the existing Bridge verification step.
- [ ] Build the affected modules and debug APK.
- [ ] Upload failure diagnostics and the successful APK artifact.
- [ ] Open a PR, fix only evidenced CI failures, and merge after a green run.
