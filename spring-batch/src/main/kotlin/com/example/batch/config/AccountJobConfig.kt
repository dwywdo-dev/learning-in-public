package com.example.batch.config

import com.example.batch.domain.Account
import com.example.batch.exception.ApiCallException
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class AccountJobConfig(
    private val jobBuilderFactory: JobBuilderFactory,
    private val stepBuilderFactory: StepBuilderFactory,
    private val dataSource: DataSource,
) {
    @Bean
    fun accountReader(): JdbcCursorItemReader<Account> =
        JdbcCursorItemReaderBuilder<Account>()
            .name("accountReader")
            .dataSource(dataSource)
            .sql("SELECT account_id, name, status, balance, created_at FROM accounts ORDER BY account_id")
            .rowMapper { rs, _ ->
                Account(
                    accountId = rs.getLong("account_id"),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    balance = rs.getLong("balance"),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                )
            }.build()

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
    fun accountStep(): Step =
        stepBuilderFactory
            .get("accountStep")
            .chunk<Account, Account>(5)
            .reader(accountReader())
            .processor(accountProcessor())
            .writer(accountWriter())
            .faultTolerant()
            .retry(ApiCallException::class.java)
            .retryLimit(3)
            .skip(ApiCallException::class.java)
            .skipLimit(5)
            .build()

    @Bean
    fun accountJob(): Job =
        jobBuilderFactory
            .get("accountJob")
            .start(accountStep())
            .build()
}
