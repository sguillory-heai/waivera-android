package com.waivera.waivera.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
        ActivityEntity::class,
        BookmarkEntity::class,
        PersonEntity::class,
        DocumentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WaiveraDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    abstract fun messages(): MessageDao

    abstract fun outbox(): OutboxDao

    abstract fun activity(): ActivityDao

    abstract fun bookmarks(): BookmarkDao

    abstract fun people(): PersonDao

    abstract fun documents(): DocumentDao

    fun transactionRunner(): TransactionRunner = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = withTransaction { block() }
    }

    companion object {
        const val NAME = "waivera-cache.db"
    }
}
