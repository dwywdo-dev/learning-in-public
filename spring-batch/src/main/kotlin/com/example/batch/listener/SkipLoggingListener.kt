package com.example.batch.listener

import com.example.batch.domain.Account
import org.springframework.batch.core.SkipListener

class SkipLoggingListener : SkipListener<Account, Account> {
    override fun onSkipInRead(t: Throwable) {
        println("[Skip-Read] ${t.message}")
    }

    override fun onSkipInProcess(
        item: Account,
        t: Throwable,
    ) {
        println("[Skip-Process] accountId=${item.accountId}, error=${t.message}")
    }

    override fun onSkipInWrite(
        item: Account,
        t: Throwable,
    ) {
        println("[Skip-Write] accountId=${item.accountId}, error=${t.message}")
    }
}
