package com.example.batch.domain

import java.time.LocalDateTime

data class Account(
    val accountId: Long,
    val name: String,
    val status: String,
    val balance: Long,
    val createdAt: LocalDateTime,
)
