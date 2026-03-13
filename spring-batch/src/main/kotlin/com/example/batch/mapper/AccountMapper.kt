package com.example.batch.mapper

import com.example.batch.domain.Account
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AccountMapper {
    fun findTargetAccounts(params: Map<String, Any>): List<Account>

    fun findAccountsByRange(params: Map<String, Any>): List<Account>
}
