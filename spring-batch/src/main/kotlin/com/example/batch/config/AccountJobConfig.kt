package com.example.batch.config

import com.example.batch.domain.Account
import com.example.batch.exception.ApiCallException
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AccountJobConfig(
    private val jobBuilderFactory: JobBuilderFactory,
    private val stepBuilderFactory: StepBuilderFactory,
) {
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
    fun accountStep(
        accountReader: MyBatisPagingItemReader<Account>,
        accountProcessor: ItemProcessor<Account, Account>,
        accountWriter: ItemWriter<Account>,
    ): Step =
        stepBuilderFactory
            .get("accountStep")
            .chunk<Account, Account>(5)
            .reader(accountReader)
            .processor(accountProcessor)
            .writer(accountWriter)
            .faultTolerant()
            .retry(ApiCallException::class.java)
            .retryLimit(3)
            .skip(ApiCallException::class.java)
            .skipLimit(5)
            .build()

    @Bean
    fun accountJob(accountStep: Step): Job =
        jobBuilderFactory
            .get("accountJob")
            .start(accountStep)
            .build()
}
