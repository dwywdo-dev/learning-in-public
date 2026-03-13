package com.example.batch.listener

import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener

class AccountStepResultListener : StepExecutionListener {
    override fun beforeStep(stepExecution: StepExecution) {
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        val jobContext = stepExecution.jobExecution.executionContext
        synchronized(jobContext) {
            val prev = jobContext.getLong("processedCount", 0)
            jobContext.putLong("processedCount", prev + stepExecution.writeCount.toLong())
            val prevSkip = jobContext.getLong("skipCount", 0)
            jobContext.putLong("skipCount", prevSkip + stepExecution.skipCount.toLong())
        }
        return null
    }
}
