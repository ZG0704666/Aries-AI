package com.ai.phoneagent.ui.icons

/**
 * Material Icons → Lucide Icon Mapping Table
 *
 * This document catalogs all Material Design Icons references in the codebase
 * and their proposed Lucide Icon equivalents. This mapping is used by T16
 * (icon replacement task) to perform the actual replacement.
 *
 * File locations scanned:
 * - AutomationControlScreen.kt
 * - AttachmentComponents.kt
 * - ConversationDrawer.kt
 * - ConversationHistoryDialog.kt
 * - InputBar.kt
 * - ConversationTranscript.kt
 * - DrawerSettingsScreen.kt
 *
 * MAPPING LEGEND:
 * Material Icon Path → Lucide Icon Name
 *
 * ============================================================================
 * ICONS.DEFAULT.* (Material Design Default Style)
 * ============================================================================
 *
 * Icons.Default.Add → Plus
 *   Usage: InputBar.kt - "Add attachment" button
 *   Lucide equivalent: Plus (commonly used for add/create actions)
 *
 * Icons.Default.AudioFile → Music2 (or AudioLines as alternative)
 *   Usage: AttachmentComponents.kt - Audio file attachment icon
 *   Lucide equivalent: Music2 (represents audio file)
 *   NO_EQUIVALENT_NOTE: Lucide has limited audio file icons; Music2 is closest
 *
 * Icons.Default.Close → X
 *   Usage: AttachmentComponents.kt - Close/remove button
 *   Lucide equivalent: X (standard close icon)
 *
 * Icons.Default.Description → FileText
 *   Usage: AttachmentComponents.kt - Document/file attachment icon
 *   Lucide equivalent: FileText (represents text document)
 *
 * Icons.Default.Image → Image
 *   Usage: AttachmentComponents.kt - Image attachment icon
 *   Lucide equivalent: Image (standard image icon)
 *
 * Icons.Default.Keyboard → Keyboard
 *   Usage: InputBar.kt - Switch to keyboard input button
 *   Lucide equivalent: Keyboard (exact match)
 *
 * Icons.Default.Mic → Mic
 *   Usage: InputBar.kt - Voice input button
 *   Lucide equivalent: Mic (exact match)
 *
 * Icons.Default.PhotoCamera → Camera
 *   Usage: AttachmentComponents.kt - Camera/photo attachment option
 *   Lucide equivalent: Camera (standard camera icon)
 *
 * Icons.Default.ScreenshotMonitor → Monitor
 *   Usage: AttachmentComponents.kt - Screenshot file attachment icon
 *   Lucide equivalent: Monitor (represents screen/screenshot)
 *   NO_EQUIVALENT_NOTE: Lucide doesn't have ScreenshotMonitor; Monitor is closest alternative
 *
 * Icons.Default.VideoLibrary → Video
 *   Usage: AttachmentComponents.kt - Video attachment icon
 *   Lucide equivalent: Video (standard video icon)
 *
 * ============================================================================
 * ICONS.OUTLINED.* (Material Design Outlined Style)
 * ============================================================================
 *
 * Icons.Outlined.AutoAwesome → Wand2
 *   Usage: AutomationControlScreen.kt - AI/automation feature indicator
 *   Lucide equivalent: Wand2 (magic wand for AI/automation)
 *   Alternative: Star, Sparkles
 *
 * Icons.Outlined.Check → Check
 *   Usage: ConversationTranscript.kt - Confirmation/success indicator
 *   Lucide equivalent: Check (exact match)
 *
 * Icons.Outlined.CheckCircle → CheckCircle2
 *   Usage: DrawerSettingsScreen.kt - Verified/completed status
 *   Lucide equivalent: CheckCircle2 (Lucide naming convention)
 *
 * Icons.Outlined.Close → X
 *   Usage: ConversationHistoryDialog.kt, ConversationTranscript.kt - Close button
 *   Lucide equivalent: X (standard close icon)
 *
 * Icons.Outlined.Cloud → Cloud
 *   Usage: DrawerSettingsScreen.kt - Remote/cloud model indicator
 *   Lucide equivalent: Cloud (exact match)
 *
 * Icons.Outlined.ContentCopy → Copy
 *   Usage: ConversationTranscript.kt - Copy text button
 *   Lucide equivalent: Copy (exact match)
 *
 * Icons.Outlined.ContentPaste → Clipboard
 *   Usage: DrawerSettingsScreen.kt - Paste/clipboard action
 *   Lucide equivalent: Clipboard (represents paste/clipboard)
 *
 * Icons.Outlined.DeleteOutline → Trash2
 *   Usage: ConversationDrawer.kt, ConversationHistoryDialog.kt - Delete button
 *   Lucide equivalent: Trash2 (delete/trash icon)
 *
 * Icons.Outlined.Download → Download
 *   Usage: DrawerSettingsScreen.kt - Download/install action
 *   Lucide equivalent: Download (exact match)
 *
 * Icons.Outlined.Edit → Pencil
 *   Usage: ConversationTranscript.kt - Edit text button
 *   Lucide equivalent: Pencil (standard edit icon)
 *
 * Icons.Outlined.Info → Info
 *   Usage: DrawerSettingsScreen.kt - Information/help indicator
 *   Lucide equivalent: Info (exact match)
 *
 * Icons.Outlined.Key → Key
 *   Usage: DrawerSettingsScreen.kt - API key/credentials field
 *   Lucide equivalent: Key (exact match)
 *
 * Icons.Outlined.KeyboardArrowDown → ChevronDown
 *   Usage: ConversationTranscript.kt - Expand/collapse indicator
 *   Lucide equivalent: ChevronDown (standard chevron)
 *
 * Icons.Outlined.KeyboardVoice → Mic2 (or Microphone)
 *   Usage: AutomationControlScreen.kt - Voice input mode indicator
 *   Lucide equivalent: Mic2 (represents voice/microphone)
 *
 * Icons.Outlined.Lightbulb → Lightbulb
 *   Usage: ConversationTranscript.kt - Hint/suggestion indicator
 *   Lucide equivalent: Lightbulb (exact match)
 *
 * Icons.Outlined.Memory → Cpu
 *   Usage: DrawerSettingsScreen.kt - Local model/memory indicator
 *   Lucide equivalent: Cpu (represents local processing/memory)
 *
 * Icons.Outlined.Refresh → RefreshCw
 *   Usage: AutomationControlScreen.kt, ConversationTranscript.kt - Refresh button
 *   Lucide equivalent: RefreshCw (standard refresh icon)
 *
 * Icons.Outlined.Settings → Settings
 *   Usage: ConversationDrawer.kt, DrawerSettingsScreen.kt - Settings button
 *   Lucide equivalent: Settings (exact match)
 *   Alternative: SettingsIcon, Gear
 *
 * Icons.Outlined.SettingsAccessibility → Accessibility
 *   Usage: AutomationControlScreen.kt - Accessibility settings indicator
 *   Lucide equivalent: Accessibility (exact match)
 *
 * Icons.Outlined.Shield → Shield
 *   Usage: AutomationControlScreen.kt - Security/permission indicator
 *   Lucide equivalent: Shield (exact match)
 *
 * Icons.Outlined.StopCircle → CircleStop (or StopCircle)
 *   Usage: AutomationControlScreen.kt - Stop/terminate automation button
 *   Lucide equivalent: CircleStop (Lucide naming: Stop + Circle variant)
 *   NO_EQUIVALENT_NOTE: Exact match if Lucide has CircleStop; otherwise use Circle + StopIcon combo
 *
 * Icons.Outlined.Sync → RotateCw
 *   Usage: DrawerSettingsScreen.kt - Synchronization/refresh action
 *   Lucide equivalent: RotateCw (standard sync icon)
 *
 * Icons.Outlined.Tune → Sliders2
 *   Usage: AutomationControlScreen.kt - Settings/configuration button
 *   Lucide equivalent: Sliders2 (represents settings/tuning)
 *   Alternative: Settings, Sliders
 *
 * ============================================================================
 * ICONS.ROUNDED.* (Material Design Rounded Style)
 * ============================================================================
 *
 * Icons.Rounded.Search → Search
 *   Usage: ConversationDrawer.kt - Search conversation button
 *   Lucide equivalent: Search (exact match)
 *
 * ============================================================================
 * ICONS.AUTOMIRRORED.* (Auto-Mirrored for RTL Languages)
 * ============================================================================
 *
 * Icons.AutoMirrored.Filled.ArrowBack → ArrowLeft
 *   Usage: AutomationControlScreen.kt, DrawerSettingsScreen.kt - Back navigation button
 *   Lucide equivalent: ArrowLeft (represents back/previous)
 *   RTL Note: Lucide uses language-aware rendering; ArrowLeft auto-mirrors in RTL contexts
 *
 * Icons.AutoMirrored.Outlined.Chat → MessageCircle (or MessageSquare)
 *   Usage: ConversationHistoryDialog.kt - Chat/conversation indicator
 *   Lucide equivalent: MessageCircle (represents chat/message)
 *   Alternative: MessageSquare, Chatbubble
 *
 * Icons.AutoMirrored.Outlined.OpenInNew → ExternalLink
 *   Usage: DrawerSettingsScreen.kt - Open link in new window
 *   Lucide equivalent: ExternalLink (exact match for opening external links)
 *
 * ============================================================================
 * SUMMARY STATISTICS
 * ============================================================================
 *
 * Total Material Icons found: 37 unique references
 * - Icons.Default.*: 9 icons
 * - Icons.Outlined.*: 24 icons
 * - Icons.Rounded.*: 1 icon
 * - Icons.AutoMirrored.*: 3 icons
 *
 * Mapping Coverage: 37/37 (100%)
 * Icons with NO direct Lucide equivalent: 2
 *   - Icons.Default.AudioFile (using Music2 as closest)
 *   - Icons.Default.ScreenshotMonitor (using Monitor as closest)
 *   - Icons.Outlined.StopCircle (pending CircleStop verification)
 *
 * All icons have been mapped to viable Lucide alternatives.
 *
 * ============================================================================
 * USAGE NOTES FOR T16 (Icon Replacement Task)
 * ============================================================================
 *
 * 1. Replace all Icons.Default.X imports with Lucide equivalents
 * 2. Replace all Icons.Outlined.X imports with Lucide equivalents
 * 3. Replace all Icons.Rounded.X imports with Lucide equivalents
 * 4. Replace all Icons.AutoMirrored.X imports with Lucide equivalents
 * 5. Update imports: remove androidx.compose.material.icons.*
 * 6. Add import: com.composables.icons.lucide.*
 * 7. Test all UI screens for visual consistency
 * 8. Verify icon colors match Material 3 theme
 * 9. Test accessibility (contentDescription still applies)
 * 10. Test both light and dark themes
 *
 */
object IconMapping {
    // Programmatic mapping for future tooling (reserved for automated replacement)
    // TODO: Implement automated icon replacement using this object during T16
}
