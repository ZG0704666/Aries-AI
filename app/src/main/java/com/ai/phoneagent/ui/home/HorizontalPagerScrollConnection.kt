package com.ai.phoneagent.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** 包裹嵌套滚动连接：剥离纵向分量，避免横向分页器惯性滑动时吞掉子项的纵向滚动。 */
internal class HorizontalPagerScrollConnection(
    private val delegate: NestedScrollConnection,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        delegate.onPreScroll(available.copy(y = 0f), source).copy(y = 0f)

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        delegate.onPostScroll(consumed.copy(y = 0f), available.copy(y = 0f), source).copy(y = 0f)

    override suspend fun onPreFling(available: Velocity): Velocity =
        delegate.onPreFling(available.copy(y = 0f)).copy(y = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        delegate.onPostFling(consumed.copy(y = 0f), available.copy(y = 0f)).copy(y = 0f)
}
