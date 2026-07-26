# MAGO Phase 5 Database Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a bounded, read-only Metasploit Database view for Workspaces, Hosts, Services, and Vulnerabilities.

**Architecture:** `core:rpc` implements the official `db.workspaces`, `db.hosts`, `db.services`, and `db.vulns` contracts. A typed domain repository feeds a dedicated `feature:inventory` ViewModel and Compose screen. Reads are capped at 100 rows, triggered only by initial entry or direct user actions, and never invoke scanning or database mutations.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose Material 3, MessagePack RPC, JUnit/Truth, GitHub Actions.

## Global Constraints

- RPC remains localhost-only.
- Inventory is read-only; no report/import, scan, host/service/vulnerability write, credential view, or workspace mutation.
- Each collection request uses `limit=100` and `offset=0`.
- There is no background polling.
- Unknown RPC fields are preserved.
- Tests cover argument bounds, parsing, workspace selection, and explicit tab reads only.
- Static Compose layout uses Build and Lint rather than screenshot tests.

## Tasks

1. Add typed inventory models and repository interface.
2. Implement official database RPC parsing with bounded options.
3. Add a manually refreshed Workspace-aware ViewModel.
4. Add adaptive inventory navigation and cards.
5. Run Bridge validation, Android Build, Lint, RPC tests, and inventory tests in GitHub Actions.

## Self-Review

- No scanning or write method is introduced.
- Credentials are intentionally excluded.
- Collection sizes are bounded.
- Testing remains risk-directed.
