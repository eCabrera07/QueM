package com.quem.ui

sealed interface QueMScreen {
    data object List : QueMScreen
    data object Create : QueMScreen
    data object Settings : QueMScreen
    data object Archive : QueMScreen
    data class Detail(val itemId: String) : QueMScreen
    data class Edit(val itemId: String) : QueMScreen
}
