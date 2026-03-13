package com.example.batch.listener

import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import java.time.Duration

class JobLoggingListener : JobExecutionListener {
    override fun beforeJob(jobExecution: JobExecution) {
        println("[Job Start] ${jobExecution.jobInstance.jobName}, params = ${jobExecution.jobParameters}")
    }

    override fun afterJob(jobExecution: JobExecution) {
        println(
            "[Job Finished] status=${jobExecution.status}, duration=${Duration.between(
                jobExecution.startTime?.toInstant(),
                jobExecution.endTime?.toInstant(),
            ).toMillis()}ms",
        )
    }
}
