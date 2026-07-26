package dev.mago.android.navigation

sealed class MagoDestination(val route: String, val label: String) {
    data object Onboarding : MagoDestination("onboarding", "設定")
    data object Dashboard : MagoDestination("dashboard", "首頁")
    data object Modules : MagoDestination("modules", "模組")
    data object Inventory : MagoDestination("inventory", "資產")
    data object Reports : MagoDestination("reports", "報告")
    data object Terminal : MagoDestination("terminal", "Console")
    data object Diagnostics : MagoDestination("diagnostics", "診斷")
}
