package com.example.core.domain

enum class AppErrorCode {
    NO_INTERNET,
    FIREBASE_UNAVAILABLE,
    INVALID_PAIRING_CODE,
    EXPIRED_PAIRING_CODE,
    UNAUTHORIZED,
    USAGE_ACCESS_DISABLED,
    PERMISSION_DENIED,
    TOKEN_EXPIRED,
    EMPTY_DATA,
    DEVICE_UNLINKED,
    UNKNOWN
}

sealed interface AppResult<out T> {
    data object Loading : AppResult<Nothing>
    data object Empty : AppResult<Nothing>
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(
        val message: String,
        val code: AppErrorCode = AppErrorCode.UNKNOWN
    ) : AppResult<Nothing>
}
