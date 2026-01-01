package com.ai.assistance.operit.util.stream

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope

/** 共享Stream接口，类似于SharedFlow */
interface SharedStream<T> : Stream<T> {
    /** 当前订阅者数量 */
    val subscriptionCount: Int

    /** 重放缓存大小 */
    val replayCache: List<T>
}

/** 可变共享Stream接口，类似于MutableSharedFlow */
interface MutableSharedStream<T> : SharedStream<T> {
    /** 发射一个值到Stream */
    suspend fun emit(value: T)

    /** 尝试发射一个值，如果缓冲区已满返回false */
    fun tryEmit(value: T): Boolean

    /** 重置重放缓存 */
    fun resetReplayCache()
}

/** 状态Stream接口，类似于StateFlow */
interface StateStream<T> : SharedStream<T> {
    /** 当前值 */
    val value: T
}

/** 可变状态Stream接口，类似于MutableStateFlow */
interface MutableStateStream<T> : StateStream<T>, MutableSharedStream<T> {
    /** 设置当前值 */
    override var value: T

    /** 比较并设置值 */
    fun compareAndSet(expect: T, update: T): Boolean
}

/**
 * Helper to access the internal kotlinx.coroutines.flow.StateFlow<Int> for subscription count This
 * is for internal use by the .state() and .share() operators.
 */
internal fun <T> SharedStream<T>.getInternalSubscriptionCountFlow():
        kotlinx.coroutines.flow.StateFlow<Int>? {
    return when (this) {
        is MutableSharedStreamImpl<T> -> this.internalFlow.subscriptionCount
        is MutableStateStreamImpl<T> -> this.internalFlow.subscriptionCount
        else -> null
    }
}

/** MutableSharedFlow的包装器，实现MutableSharedStream */
class MutableSharedStreamImpl<T>(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : MutableSharedStream<T> {
    internal val internalFlow = MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)
    private val completion = CompletableDeferred<Throwable?>()

    // 热流不需要锁定机制，所以这里提供默认实现
    override val isLocked: Boolean = false
    override val bufferedCount: Int = 0

    override suspend fun lock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "热流不支持锁定操作")
    }

    override suspend fun unlock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "热流不支持解锁操作")
    }

    override fun clearBuffer() {
        // 热流有自己的缓冲管理，此方法不适用
        StreamLogger.d("HotStream", "热流不支持清空缓冲区操作")
    }

    override val subscriptionCount: Int
        get() = internalFlow.subscriptionCount.value

    override val replayCache: List<T>
        get() = internalFlow.replayCache

    override suspend fun emit(value: T) {
        internalFlow.emit(value)
    }

    override fun tryEmit(value: T): Boolean {
        return internalFlow.tryEmit(value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun resetReplayCache() {
        internalFlow.resetReplayCache()
    }

    fun close(cause: Throwable? = null) {
        completion.complete(cause)
    }

    override suspend fun collect(collector: StreamCollector<T>) {
        coroutineScope {
            val collectJob = launch {
                internalFlow.collect { value -> collector.emit(value) }
            }

            val cause = completion.await()
            collectJob.cancel()
            if (cause != null) {
                throw cause
            }
        }
    }
}

/** MutableStateFlow的包装器，实现MutableStateStream */
class MutableStateStreamImpl<T>(initialValue: T) : MutableStateStream<T> {
    internal val internalFlow = MutableStateFlow(initialValue)

    // 热流不需要锁定机制，所以这里提供默认实现
    override val isLocked: Boolean = false
    override val bufferedCount: Int = 0

    override suspend fun lock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "状态流不支持锁定操作")
    }

    override suspend fun unlock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "状态流不支持解锁操作")
    }

    override fun clearBuffer() {
        // 热流有自己的缓冲管理，此方法不适用
        StreamLogger.d("HotStream", "状态流不支持清空缓冲区操作")
    }

    override var value: T
        get() = internalFlow.value
        set(value) {
            internalFlow.value = value
        }

    override val subscriptionCount: Int
        get() = internalFlow.subscriptionCount.value

    override val replayCache: List<T>
        get() = internalFlow.replayCache

    override suspend fun emit(value: T) {
        internalFlow.emit(value)
    }

    override fun tryEmit(value: T): Boolean {
        return internalFlow.tryEmit(value)
    }

    override fun resetReplayCache() {
        // StateFlow does not support resetting replay cache as it always holds the current state.
    }

    override fun compareAndSet(expect: T, update: T): Boolean {
        return internalFlow.compareAndSet(expect, update)
    }

    override suspend fun collect(collector: StreamCollector<T>) {
        internalFlow.collect { value -> collector.emit(value) }
    }
}

/** 创建一个MutableSharedStream */
fun <T> MutableSharedStream(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
        context: CoroutineContext = EmptyCoroutineContext
): MutableSharedStream<T> {
    return MutableSharedStreamImpl<T>(replay, extraBufferCapacity, onBufferOverflow)
}

/** 创建一个MutableStateStream */
fun <T> MutableStateStream(initialValue: T): MutableStateStream<T> {
    return MutableStateStreamImpl(initialValue)
}

/** 将Stream转变为热流，类似于Flow的shareIn */
fun <T> Stream<T>.share(
        scope: CoroutineScope,
        replay: Int = 0,
        started: StreamStart = StreamStart.EAGERLY,
        onComplete: suspend () -> Unit = {}
): SharedStream<T> {
    val sharedStream = MutableSharedStreamImpl<T>(replay = replay)
    var upstreamJob: Job? = null

    when (started) {
        StreamStart.EAGERLY -> {
            // 这个Job现在是scope的直接子Job
            upstreamJob =
                    scope.launch {
                        try {
                            this@share.collect { value -> sharedStream.emit(value) }
                        } finally {
                            // 当上游流完成或被取消时，我们不再需要这个共享流。
                            // 但由于SharedFlow本身不会"关闭"，依赖协程的结构化并发来清理是最好的方式。
                            // 此处的finally确保了协程在任何情况下（完成、取消、异常）都能结束。
                            StreamLogger.d("Stream.share", "上游流收集完成或取消，共享流协程结束。")
                            sharedStream.close() // 关闭流以允许收集器完成
                            onComplete()
                        }
                    }
        }
        StreamStart.LAZILY -> {
            scope.launch {
                val subscriptionCountFlow = sharedStream.getInternalSubscriptionCountFlow()
                if (subscriptionCountFlow != null) {
                    subscriptionCountFlow.collect { count ->
                        if (count > 0 && upstreamJob?.isActive != true) {
                            upstreamJob =
                                    scope.launch {
                                        try {
                                            this@share.collect { emittedValue ->
                                                sharedStream.emit(emittedValue)
                                            }
                                        } finally {
                                            StreamLogger.d("Stream.share", "上游流(LAZILY)收集完成或取消。")
                                            sharedStream.close() // 关闭流以允许收集器完成
                                            onComplete()
                                        }
                                    }
                        } else if (count == 0) {
                            // 当没有订阅者时，取消上游流的收集
                            upstreamJob?.cancel()
                            upstreamJob = null
                            StreamLogger.d("Stream.share", "没有订阅者，已取消上游流(LAZILY)的收集。")
                        }
                    }
                } else {
                    println(
                            "Warning: Stream.share LAZILY mode could not observe subscriptions, may behave like EAGERLY."
                    )
                    // Fallback to EAGERLY behavior
                    scope.launch {
                        try {
                            this@share.collect { value -> sharedStream.emit(value) }
                        } finally {
                            sharedStream.close() // 关闭流以允许收集器完成
                            onComplete()
                        }
                    }
                }
            }
        }
    }

    return sharedStream
}

/** 将Stream转变为StateStream，类似于Flow的stateIn */
fun <T> Stream<T>.state(
        scope: CoroutineScope,
        initialValue: T,
        started: StreamStart = StreamStart.EAGERLY
): StateStream<T> {
    val stateStream = MutableStateStreamImpl(initialValue)
    var upstreamJob: Job? = null

    when (started) {
        StreamStart.EAGERLY -> {
            scope.launch { this@state.collect { value -> stateStream.value = value } }
        }
        StreamStart.LAZILY -> {
            scope.launch {
                val subscriptionCountFlow = stateStream.getInternalSubscriptionCountFlow()
                if (subscriptionCountFlow != null) {
                    subscriptionCountFlow.collect { count ->
                        if (count > 0 && upstreamJob == null) {
                            upstreamJob =
                                    scope.launch {
                                        this@state.collect { emittedValue ->
                                            stateStream.value = emittedValue
                                        }
                                    }
                        }
                    }
                } else {
                    println(
                            "Warning: Stream.state LAZILY mode could not observe subscriptions, may behave like EAGERLY."
                    )
                    upstreamJob =
                            scope.launch {
                                this@state.collect { value -> stateStream.value = value }
                            }
                }
            }
        }
    }

    return stateStream
}

/** 流启动模式 */
enum class StreamStart {
    /** 立即启动 */
    EAGERLY,

    /** 有订阅者时启动 */
    LAZILY
}
