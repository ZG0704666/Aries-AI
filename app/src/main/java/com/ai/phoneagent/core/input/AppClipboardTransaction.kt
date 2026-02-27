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
package com.ai.phoneagent.core.input

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.ai.phoneagent.AutomationOverlay

/**
 * Clipboard transaction for temporary text input:
 * 1) snapshot current clipboard
 * 2) write + verify temporary text
 * 3) run paste action
 * 4) restore original clipboard in finally
 */
class AppClipboardTransaction(private val context: Context) {

    enum class VerifyState {
        MATCHED,
        UNREADABLE,
        MISMATCH,
        WRITE_FAILED,
        NO_CLIPBOARD_SERVICE
    }

    data class Result(
            val staged: Boolean,
            val verifyState: VerifyState,
            val actionSucceeded: Boolean,
            val restored: Boolean
    )

    private data class Snapshot(
            val hadPrimaryClip: Boolean,
            val primaryClip: ClipData?
    )

    fun run(
            temporaryText: String,
            verifyRetries: Int = 5,
            verifyIntervalMs: Long = 40L,
            action: (VerifyState) -> Boolean
    ): Result {
        val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        ?: return Result(
                                staged = false,
                                verifyState = VerifyState.NO_CLIPBOARD_SERVICE,
                                actionSucceeded = false,
                                restored = false
                        )

        val snapshot = captureSnapshot(clipboard)
        val verifyState =
                stageTemporaryText(
                        clipboard = clipboard,
                        text = temporaryText,
                        retries = verifyRetries,
                        intervalMs = verifyIntervalMs
                )
        val staged = verifyState == VerifyState.MATCHED || verifyState == VerifyState.UNREADABLE

        if (!staged) {
            val restored = restoreSnapshot(clipboard, snapshot)
            return Result(
                    staged = false,
                    verifyState = verifyState,
                    actionSucceeded = false,
                    restored = restored
            )
        }

        val shouldHighlightVerifySuccess = verifyState == VerifyState.MATCHED
        if (shouldHighlightVerifySuccess) {
            AutomationOverlay.setInputVerifyHighlight(true)
        }

        var actionSucceeded = false
        var restored = false
        try {
            actionSucceeded = runCatching { action(verifyState) }.getOrDefault(false)
        } finally {
            restored = restoreSnapshot(clipboard, snapshot)
            if (shouldHighlightVerifySuccess) {
                AutomationOverlay.setInputVerifyHighlight(false)
            }
        }
        return Result(
                staged = true,
                verifyState = verifyState,
                actionSucceeded = actionSucceeded,
                restored = restored
        )
    }

    private fun captureSnapshot(clipboard: ClipboardManager): Snapshot {
        val original = runCatching { clipboard.primaryClip }.getOrNull()
        return Snapshot(hadPrimaryClip = original != null, primaryClip = original)
    }

    private fun stageTemporaryText(
            clipboard: ClipboardManager,
            text: String,
            retries: Int,
            intervalMs: Long
    ): VerifyState {
        val wrote =
                runCatching {
                    clipboard.setPrimaryClip(ClipData.newPlainText("AriesInputTemp", text))
                    true
                }.getOrDefault(false)
        if (!wrote) return VerifyState.WRITE_FAILED

        return when (verifyText(clipboard, text, retries, intervalMs)) {
            true -> VerifyState.MATCHED
            false -> VerifyState.MISMATCH
            null -> VerifyState.UNREADABLE
        }
    }

    private fun verifyText(
            clipboard: ClipboardManager,
            expected: String,
            retries: Int,
            intervalMs: Long
    ): Boolean? {
        var hasReadableResult = false
        repeat(retries.coerceAtLeast(1)) {
            val readBack =
                    runCatching {
                        clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                    }.getOrNull()
            if (readBack != null) {
                hasReadableResult = true
                if (readBack == expected) return true
            }
            try {
                Thread.sleep(intervalMs.coerceAtLeast(0L))
            } catch (_: InterruptedException) {}
        }
        return if (hasReadableResult) false else null
    }

    private fun restoreSnapshot(
            clipboard: ClipboardManager,
            snapshot: Snapshot
    ): Boolean {
        return runCatching {
            if (!snapshot.hadPrimaryClip || snapshot.primaryClip == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } else {
                clipboard.setPrimaryClip(snapshot.primaryClip)
            }
            true
        }.getOrDefault(false)
    }
}
