package com.example.core.navigation

import kotlinx.serialization.Serializable

sealed interface FamilyGuardRoute {
    @Serializable
    data object RolePicker : FamilyGuardRoute

    @Serializable
    data object ParentShell : FamilyGuardRoute

    @Serializable
    data object PrivacyData : FamilyGuardRoute

    @Serializable
    data object ChildShell : FamilyGuardRoute
}

enum class ParentTab {
    DASHBOARD,
    DEVICES,
    LIMITS,
    ALERTS,
    SETTINGS
}
