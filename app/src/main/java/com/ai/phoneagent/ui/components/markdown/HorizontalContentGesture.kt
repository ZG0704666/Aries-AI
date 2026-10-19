package com.ai.phoneagent.ui.components.markdown

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** 嵌套滚动边界：仅保留横向滚动，将未消费的横向分量交还父级。 */
private object HorizontalContentBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

/** 让内容区域保留横向手势，避免被纵向滚动容器拦截。 */
fun Modifier.keepHorizontalGestures(): Modifier = nestedScroll(HorizontalContentBoundary)
