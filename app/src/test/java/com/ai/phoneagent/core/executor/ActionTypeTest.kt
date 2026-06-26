/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.executor

import org.junit.Assert.*
import org.junit.Test

class ActionTypeTest {

    @Test
    fun `fromName returns TAP for tap`() {
        assertEquals(ActionType.TAP, ActionType.fromName("tap"))
    }

    @Test
    fun `fromName returns TAP for click`() {
        assertEquals(ActionType.TAP, ActionType.fromName("click"))
    }

    @Test
    fun `fromName returns TAP for press`() {
        assertEquals(ActionType.TAP, ActionType.fromName("press"))
    }

    @Test
    fun `fromName returns LONG_PRESS for long_press`() {
        assertEquals(ActionType.LONG_PRESS, ActionType.fromName("long_press"))
    }

    @Test
    fun `fromName returns LONG_PRESS for longpress`() {
        assertEquals(ActionType.LONG_PRESS, ActionType.fromName("longpress"))
    }

    @Test
    fun `fromName returns DOUBLE_TAP for double_tap`() {
        assertEquals(ActionType.DOUBLE_TAP, ActionType.fromName("double_tap"))
    }

    @Test
    fun `fromName returns SWIPE for swipe`() {
        assertEquals(ActionType.SWIPE, ActionType.fromName("swipe"))
    }

    @Test
    fun `fromName returns SWIPE for scroll`() {
        assertEquals(ActionType.SWIPE, ActionType.fromName("scroll"))
    }

    @Test
    fun `fromName returns TYPE for type`() {
        assertEquals(ActionType.TYPE, ActionType.fromName("type"))
    }

    @Test
    fun `fromName returns TYPE for input`() {
        assertEquals(ActionType.TYPE, ActionType.fromName("input"))
    }

    @Test
    fun `fromName returns LAUNCH for launch`() {
        assertEquals(ActionType.LAUNCH, ActionType.fromName("launch"))
    }

    @Test
    fun `fromName returns LAUNCH for open_app`() {
        assertEquals(ActionType.LAUNCH, ActionType.fromName("open_app"))
    }

    @Test
    fun `fromName returns BACK for back`() {
        assertEquals(ActionType.BACK, ActionType.fromName("back"))
    }

    @Test
    fun `fromName returns HOME for home`() {
        assertEquals(ActionType.HOME, ActionType.fromName("home"))
    }

    @Test
    fun `fromName returns WAIT for wait`() {
        assertEquals(ActionType.WAIT, ActionType.fromName("wait"))
    }

    @Test
    fun `fromName returns WAIT for sleep`() {
        assertEquals(ActionType.WAIT, ActionType.fromName("sleep"))
    }

    @Test
    fun `fromName returns TAKE_OVER for take_over`() {
        assertEquals(ActionType.TAKE_OVER, ActionType.fromName("take_over"))
    }

    @Test
    fun `fromName returns FINISH for finish`() {
        assertEquals(ActionType.FINISH, ActionType.fromName("finish"))
    }

    @Test
    fun `fromName returns UNKNOWN for null`() {
        assertEquals(ActionType.UNKNOWN, ActionType.fromName(null))
    }

    @Test
    fun `fromName returns UNKNOWN for unrecognized`() {
        assertEquals(ActionType.UNKNOWN, ActionType.fromName("unknown_action"))
    }

    @Test
    fun `fromName handles case insensitive`() {
        assertEquals(ActionType.TAP, ActionType.fromName("TAP"))
        assertEquals(ActionType.TAP, ActionType.fromName("Click"))
    }

    @Test
    fun `fromName handles whitespace`() {
        assertEquals(ActionType.LONG_PRESS, ActionType.fromName("  long_press  "))
        assertEquals(ActionType.DOUBLE_TAP, ActionType.fromName("double tap"))
    }

    @Test
    fun `fromName handles quotes`() {
        assertEquals(ActionType.TAP, ActionType.fromName("\"tap\""))
        assertEquals(ActionType.TAP, ActionType.fromName("'tap'"))
    }
}
