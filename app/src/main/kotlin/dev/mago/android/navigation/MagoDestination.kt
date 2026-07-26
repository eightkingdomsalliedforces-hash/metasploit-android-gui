package dev.mago.android.navigation

sealed class MagoDestination(val route: String, val label: String) {
    data object Onboarding : MagoDestination("onboarding", "設定")
    data object Dashboard : MagoDestination("dashboard", "首頁")
    data object Modules : MagoDestination("modules", "模組")
    data object Operations : MagoDestination("operations", "Operations")
    data object Terminal : MagoDestination("terminal", "Console")
    data object Diagnostics : MagoDestination("diagnostics", "診斷")
}
