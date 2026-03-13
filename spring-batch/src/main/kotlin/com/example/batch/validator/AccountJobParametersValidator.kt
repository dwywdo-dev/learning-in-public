package com.example.batch.validator

import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersValidator

class AccountJobParametersValidator : JobParametersValidator {
    override fun validate(parameters: JobParameters?) {
        val minBalanceStr = parameters?.getString("minBalance")
        val minBalance = minBalanceStr?.toLongOrNull()
        if (minBalance == null || minBalance < 0) {
            throw JobParametersInvalidException(
                "Required parameter 'minBalance' is missing or invalid (must be > 0)",
            )
        }
    }
}
