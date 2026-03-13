package com.example.batch.config

import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HelloJobConfig(
    private val jobBuilderFactory: JobBuilderFactory,
    private val stepBuilderFactory: StepBuilderFactory,
) {
    @Bean
    fun helloStep(): Step =
        stepBuilderFactory
            .get("helloStep")
            .tasklet { contribution, chunkContext ->
                println("Hello, Spring Batch!")
                RepeatStatus.FINISHED
            }.build()

    @Bean
    fun helloJob(helloStep: Step): Job =
        jobBuilderFactory
            .get("helloJob")
            .start(helloStep)
            .build()
}
