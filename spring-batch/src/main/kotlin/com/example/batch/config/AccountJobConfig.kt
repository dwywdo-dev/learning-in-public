package com.example.batch.config

import com.example.batch.domain.Account
import com.example.batch.exception.ApiCallException
import com.example.batch.listener.AccountStepResultListener
import com.example.batch.listener.JobLoggingListener
import com.example.batch.listener.SkipLoggingListener
import com.example.batch.partition.AccountPartitioner
import com.example.batch.validator.AccountJobParametersValidator
import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.batch.MyBatisPagingItemReader
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import javax.sql.DataSource

@Configuration
class AccountJobConfig(
    private val jobBuilderFactory: JobBuilderFactory,
    private val stepBuilderFactory: StepBuilderFactory,
) {
    @Bean
    @StepScope
    fun partitionReader(
        sqlSessionFactory: SqlSessionFactory,
        @Value("#{stepExecutionContext['minId']}") minId: Long,
        @Value("#{stepExecutionContext['maxId']}") maxId: Long,
    ): MyBatisPagingItemReader<Account> =
        MyBatisPagingItemReaderBuilder<Account>()
            .sqlSessionFactory(sqlSessionFactory)
            .queryId("com.example.batch.mapper.AccountMapper.findAccountsByRange")
            .parameterValues(mapOf("minId" to minId, "maxId" to maxId))
            .pageSize(5)
            .build()

    @Bean
    fun accountReader(sqlSessionFactory: SqlSessionFactory): MyBatisPagingItemReader<Account> =
        MyBatisPagingItemReaderBuilder<Account>()
            .sqlSessionFactory(sqlSessionFactory)
            .queryId("com.example.batch.mapper.AccountMapper.findTargetAccounts")
            .pageSize(5)
            .build()

    @Bean
    @StepScope
    fun accountProcessor(
        @Value("#{jobParameters['minBalance']}") minBalance: Long,
    ): ItemProcessor<Account, Account> =
        ItemProcessor { account ->
            if (account.status == "ACTIVE" && account.balance >= minBalance) {
                account
            } else {
                null
            }
        }

    @Bean
    fun accountWriter(): ItemWriter<Account> {
        var chunkCount = 0
        return ItemWriter { accounts ->
            chunkCount++
            println("Chunk: $chunkCount (${accounts.size}건)")
            accounts.forEach { account ->
                if (account.accountId == 8L) {
                    println("[API Failure]: $account")
                    throw ApiCallException("API Call Failure: $account")
                }
                println("[API Call]: $account")
            }
        }
    }

    @Bean
    fun initStep(): Step =
        stepBuilderFactory
            .get("initStep")
            .tasklet { _, _ ->
                println("[InitStep] Starting Batch...")
                RepeatStatus.FINISHED
            }.build()

    @Bean
    fun accountStep(
        partitionReader: MyBatisPagingItemReader<Account>,
        accountProcessor: ItemProcessor<Account, Account>,
        accountWriter: ItemWriter<Account>,
    ): Step =
        stepBuilderFactory
            .get("accountStep")
            .listener(AccountStepResultListener())
            .chunk<Account, Account>(5)
            .reader(partitionReader)
            .processor(accountProcessor)
            .writer(accountWriter)
            .faultTolerant()
            .retry(ApiCallException::class.java)
            .retryLimit(3)
            .skip(ApiCallException::class.java)
            .skipLimit(5)
            .listener(SkipLoggingListener())
            .build()

    @Bean
    fun reportStep(): Step =
        stepBuilderFactory
            .get("reportStep")
            .tasklet { _, chunkContext ->
                val jobContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
                val processedCount = jobContext.getLong("processedCount", 0)
                val skipCount = jobContext.getLong("skipCount", 0)
                println("[ReportStep] Finished Batch! Processed: $processedCount Skipped: $skipCount ")
                RepeatStatus.FINISHED
            }.build()

    @Bean
    fun errorStep(): Step =
        stepBuilderFactory
            .get("errorStep")
            .tasklet { _, _ ->
                println("[ErrorStep] Error occurred during Batch!")
                RepeatStatus.FINISHED
            }.build()

    @Bean
    fun partitionStep(
        accountStep: Step,
        dataSource: DataSource,
    ): Step =
        stepBuilderFactory
            .get("partitionStep")
            .partitioner("accountStep", AccountPartitioner(dataSource))
            .step(accountStep)
            .gridSize(4)
            .taskExecutor(SimpleAsyncTaskExecutor())
            .build()

    @Bean
    fun accountJob(
        initStep: Step,
        accountStep: Step,
        partitionStep: Step,
        reportStep: Step,
        errorStep: Step,
    ): Job =
        jobBuilderFactory
            .get("accountJob")
            .validator(AccountJobParametersValidator()) // Or, DefaultJobParametersValidator(arrayOf("minBalance"))
            .listener(JobLoggingListener())
            .start(initStep)
            .next(partitionStep)
            .on("COMPLETED")
            .to(reportStep)
            .from(partitionStep)
            .on("FAILED")
            .to(errorStep)
            .end()
            .build()
}
