package com.example.batch.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows

@SpringBatchTest
@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
class AccountJobTest {

    @Autowired
    lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    lateinit var jobRepositoryTestUtils: JobRepositoryTestUtils

    @AfterEach
    fun cleanup() {
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @Test
    fun `minBalance 파라미터로 Job 실행 시 COMPLETED`() {
        val params = JobParametersBuilder()
            .addString("minBalance", "500000")
            .toJobParameters()

        val execution = jobLauncherTestUtils.launchJob(params)

        assertThat(execution.status).isEqualTo(BatchStatus.COMPLETED)
    }

    @Test
    fun `minBalance 파라미터 누락 시 JobParametersInvalidException`() {
        assertThrows<JobParametersInvalidException> {
            jobLauncherTestUtils.launchJob(JobParameters())
        }
    }

    @Test
    fun `Job 실행 후 처리 건수와 스킵 건수 검증`() {
        val params = JobParametersBuilder()
            .addString("minBalance", "500000")
            .toJobParameters()

        val execution = jobLauncherTestUtils.launchJob(params)

        assertThat(execution.executionContext.getLong("processedCount")).isEqualTo(3L)
        assertThat(execution.executionContext.getLong("skipCount")).isEqualTo(1L)
    }
}
