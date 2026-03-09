package com.example.beebrother

data class UploadLog(
    val timestamp: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)