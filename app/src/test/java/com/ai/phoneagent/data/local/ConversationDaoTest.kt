package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric 测试 [ConversationDao] 的 Room 持久化行为。
 *
 * 使用内存数据库（inMemoryDatabaseBuilder）避免磁盘 IO，保证测试隔离与速度。
 * 通过 [runTest] 驱动 suspend 函数，验证 CRUD 语义与排序逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationDaoTest {

    private lateinit var database: AriesDatabase
    private lateinit var dao: ConversationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AriesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.conversationDao()
    }

    @After
    fun tearDown() {
        database.close()
        // Robolectric 创建 Application 时 AriesAgentApp.onCreate() 会 startKoin()，
        // 多个 Robolectric 测试类并行运行时需在 @After 中 stopKoin() 避免残留状态。
        stopKoin()
    }

    private fun entity(id: Long, title: String = "t$id", updatedAt: Long, json: String = "[]"): ConversationEntity =
        ConversationEntity(id = id, title = title, updatedAt = updatedAt, messagesJson = json)

    // ─── getAll / upsertAll ────────────────────────────────────────────────

    @Test
    fun `空数据库_getAll返回空列表`() = runTest {
        val result = dao.getAll()

        assertTrue("空数据库应返回空列表", result.isEmpty())
    }

    @Test
    fun `upsertAll插入单条_getAll返回该条`() = runTest {
        val item = entity(id = 1, updatedAt = 1000L)

        dao.upsertAll(listOf(item))

        val result = dao.getAll()
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("t1", result[0].title)
        assertEquals(1000L, result[0].updatedAt)
        assertEquals("[]", result[0].messagesJson)
    }

    @Test
    fun `upsertAll批量插入_getAll全部返回`() = runTest {
        val items = listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 2000L),
            entity(id = 3, updatedAt = 3000L),
        )

        dao.upsertAll(items)

        val result = dao.getAll()
        assertEquals(3, result.size)
    }

    // ─── 排序（updatedAt DESC）────────────────────────────────────────────

    @Test
    fun `getAll按updatedAt降序排列`() = runTest {
        val items = listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 3000L),
            entity(id = 3, updatedAt = 2000L),
        )

        dao.upsertAll(items)

        val result = dao.getAll()
        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `updatedAt相同_排序稳定`() = runTest {
        val items = listOf(
            entity(id = 10, updatedAt = 5000L),
            entity(id = 20, updatedAt = 5000L),
            entity(id = 30, updatedAt = 5000L),
        )

        dao.upsertAll(items)

        val result = dao.getAll()
        assertEquals(3, result.size)
        assertTrue("所有条目 updatedAt 相同", result.all { it.updatedAt == 5000L })
    }

    // ─── upsert 语义 ──────────────────────────────────────────────────────

    @Test
    fun `upsertAll对已存在主键执行更新`() = runTest {
        dao.upsertAll(listOf(entity(id = 1, title = "旧标题", updatedAt = 1000L)))

        dao.upsertAll(listOf(entity(id = 1, title = "新标题", updatedAt = 2000L, json = "[{\"r\":1}]")))

        val result = dao.getAll()
        assertEquals(1, result.size)
        assertEquals("新标题", result[0].title)
        assertEquals(2000L, result[0].updatedAt)
        assertEquals("[{\"r\":1}]", result[0].messagesJson)
    }

    @Test
    fun `upsertAll混合插入与更新`() = runTest {
        dao.upsertAll(listOf(entity(id = 1, updatedAt = 1000L), entity(id = 2, updatedAt = 2000L)))

        dao.upsertAll(listOf(
            entity(id = 2, title = "updated", updatedAt = 5000L),
            entity(id = 3, title = "new", updatedAt = 4000L),
        ))

        val result = dao.getAll()
        assertEquals(3, result.size)
        val byId = result.associateBy { it.id }
        assertEquals("updated", byId[2L]?.title)
        assertEquals("new", byId[3L]?.title)
        assertEquals("t1", byId[1L]?.title)
    }

    // ─── clearAll ─────────────────────────────────────────────────────────

    @Test
    fun `clearAll清空全部条目`() = runTest {
        dao.upsertAll(listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 2000L),
        ))
        assertEquals(2, dao.getAll().size)

        dao.clearAll()

        assertTrue("clearAll 后应为空", dao.getAll().isEmpty())
    }

    @Test
    fun `clearAll对空数据库无副作用`() = runTest {
        dao.clearAll()

        assertTrue(dao.getAll().isEmpty())
    }

    // ─── deleteMissing ─────────────────────────────────────────────────────

    @Test
    fun `deleteMissing删除不在保留列表中的条目`() = runTest {
        dao.upsertAll(listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 2000L),
            entity(id = 3, updatedAt = 3000L),
            entity(id = 4, updatedAt = 4000L),
        ))

        dao.deleteMissing(listOf(1L, 3L))

        val result = dao.getAll()
        assertEquals(2, result.size)
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    @Test
    fun `deleteMissing保留全部_传入所有id`() = runTest {
        dao.upsertAll(listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 2000L),
        ))

        dao.deleteMissing(listOf(1L, 2L))

        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun `deleteMissing删除全部_传入空列表`() = runTest {
        dao.upsertAll(listOf(
            entity(id = 1, updatedAt = 1000L),
            entity(id = 2, updatedAt = 2000L),
        ))

        dao.deleteMissing(emptyList())

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `deleteMissing传入不存在的id_不影响现有条目`() = runTest {
        dao.upsertAll(listOf(entity(id = 1, updatedAt = 1000L)))

        dao.deleteMissing(listOf(1L, 999L, 1000L))

        assertEquals(1, dao.getAll().size)
    }

    // ─── messagesJson 完整性 ───────────────────────────────────────────────

    @Test
    fun `messagesJson大文本正确持久化`() = runTest {
        val largeJson = buildString { repeat(500) { append("{\"msg\":$it},") } }
        dao.upsertAll(listOf(entity(id = 1, updatedAt = 1000L, json = largeJson)))

        val result = dao.getAll()

        assertEquals(largeJson, result[0].messagesJson)
    }
}
