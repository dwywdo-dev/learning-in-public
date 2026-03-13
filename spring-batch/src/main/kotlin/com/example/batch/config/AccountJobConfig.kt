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
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
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
    fun accountProcessor(): ItemProcessor<Account, Account> =
        ItemProcessor { account ->
            if (account.status == "ACTIVE" && account.balance >= 100_000) {
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
    fun accountStep(sqlSessionFactory: SqlSessionFactory): Step =
        stepBuilderFactory
            .get("accountStep")
            .chunk<Account, Account>(5)
            .reader(accountReader(sqlSessionFactory))
            .processor(accountProcessor())
            .writer(accountWriter())
            .faultTolerant()
            .retry(ApiCallException::class.java)
            .retryLimit(3)
            .skip(ApiCallException::class.java)
            .skipLimit(5)
            .build()

    @Bean
    fun accountJob(sqlSessionFactory: SqlSessionFactory): Job =
        jobBuilderFactory
            .get("accountJob")
            .start(accountStep(sqlSessionFactory))
            .build()
}
