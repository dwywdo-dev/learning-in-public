package com.example.batch.partition

import org.springframework.batch.core.partition.support.Partitioner
import org.springframework.batch.item.ExecutionContext
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

class AccountPartitioner(
    private val dataSource: DataSource,
) : Partitioner {
    override fun partition(gridSize: Int): Map<String, ExecutionContext> {
        // DB에서 ID 범위를 조회
        val (minId, maxId) =
            JdbcTemplate(dataSource).queryForObject(
                "SELECT MIN(account_id), MAX(account_id) FROM accounts",
            ) { rs, _ -> rs.getLong(1) to rs.getLong(2) }!!

        val partitionSize = (maxId - minId + 1) / gridSize + 1
        val partitions = mutableMapOf<String, ExecutionContext>()

        for (i in 0 until gridSize) {
            val context = ExecutionContext()
            context.putLong("minId", minId + i * partitionSize)
            context.putLong("maxId", minOf(minId + (i + 1) * partitionSize, maxId))
            partitions["partition$i"] = context
        }

        return partitions
    }
}
