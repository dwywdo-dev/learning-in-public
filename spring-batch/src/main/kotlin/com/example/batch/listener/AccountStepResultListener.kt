package com.example.batch.listener

import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener

class AccountStepResultListener : StepExecutionListener {
    override fun beforeStep(stepExecution: StepExecution) {
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        val jobContext = stepExecution.jobExecution.executionContext
        jobContext.putLong("processedCount", stepExecution.writeCount.toLong())
        jobContext.putLong("skipCount", stepExecution.skipCount.toLong())
        return null
    }
}
