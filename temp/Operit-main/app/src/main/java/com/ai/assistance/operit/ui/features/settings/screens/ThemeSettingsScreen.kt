package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.settings.components.MediaTypeOption
import com.ai.assistance.operit.ui.features.settings.components.ThemeModeOption
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption
import com.ai.assistance.operit.ui.features.settings.components.AvatarPicker
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.launch

// Add utility function to calculate the luminance of a color
private fun calculateLuminance(color: Color): Float {
    return 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 添加角色卡管理器
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }

    // 获取当前活跃角色卡
    val activeCharacterCard = characterCardManager.activeCharacterCardFlow.collectAsState(
        initial = CharacterCard(
            id = "default_character",
            name = "默认角色卡",
            description = "",
            characterSetting = "",
            otherContent = "",
            attachedTagIds = emptyList(),
            advancedCustomPrompt = "",
            marks = "",
            isDefault = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    ).value

    // Collect theme settings
    val themeMode =
            preferencesManager.themeMode.collectAsState(
                            initial = UserPreferencesManager.THEME_MODE_LIGHT
                    )
                    .value
    val useSystemTheme = preferencesManager.useSystemTheme.collectAsState(initial = true).value
    val customPrimaryColor =
            preferencesManager.customPrimaryColor.collectAsState(initial = null).value
    val customSecondaryColor =
            preferencesManager.customSecondaryColor.collectAsState(initial = null).value
    val useCustomColors = preferencesManager.useCustomColors.collectAsState(initial = false).value

    // Collect background image settings
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val backgroundImageOpacity =
            preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f).value

    // Collect background media type and video settings
    val backgroundMediaType =
            preferencesManager.backgroundMediaType.collectAsState(
                            initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
                    )
                    .value
    val videoBackgroundMuted =
            preferencesManager.videoBackgroundMuted.collectAsState(initial = true).value
    val videoBackgroundLoop =
            preferencesManager.videoBackgroundLoop.collectAsState(initial = true).value

    // Collect toolbar transparency setting
    val toolbarTransparent =
            preferencesManager.toolbarTransparent.collectAsState(initial = false).value
    
    // Collect AppBar custom color settings
    val useCustomAppBarColor =
            preferencesManager.useCustomAppBarColor.collectAsState(initial = false).value
    val customAppBarColor =
            preferencesManager.customAppBarColor.collectAsState(initial = null).value

    // Collect status bar color settings
    val useCustomStatusBarColor =
            preferencesManager.useCustomStatusBarColor.collectAsState(initial = false).value
    val customStatusBarColor =
            preferencesManager.customStatusBarColor.collectAsState(initial = null).value
    val statusBarTransparent =
            preferencesManager.statusBarTransparent.collectAsState(initial = false).value
    val statusBarHidden =
            preferencesManager.statusBarHidden.collectAsState(initial = false).value
    val chatHeaderTransparent =
            preferencesManager.chatHeaderTransparent.collectAsState(initial = false).value
    val chatInputTransparent =
            preferencesManager.chatInputTransparent.collectAsState(initial = false).value
    val chatHeaderOverlayMode =
            preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false).value

    // Collect AppBar content color settings
    val forceAppBarContentColor =
            preferencesManager.forceAppBarContentColor.collectAsState(initial = false).value
    val appBarContentColorMode =
            preferencesManager.appBarContentColorMode.collectAsState(
                            initial = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                    )
                    .value

    // Collect ChatHeader icon color settings
    val chatHeaderHistoryIconColor =
            preferencesManager.chatHeaderHistoryIconColor.collectAsState(initial = null).value
    val chatHeaderPipIconColor =
            preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null).value

    // Collect background blur settings
    val useBackgroundBlur =
            preferencesManager.useBackgroundBlur.collectAsState(initial = false).value
    val backgroundBlurRadius =
            preferencesManager.backgroundBlurRadius.collectAsState(initial = 10f).value

    // Collect chat style setting
    val chatStyle = preferencesManager.chatStyle.collectAsState(initial = UserPreferencesManager.CHAT_STYLE_CURSOR).value

    // Collect new display settings
    val showThinkingProcess = preferencesManager.showThinkingProcess.collectAsState(initial = true).value
    val showStatusTags = preferencesManager.showStatusTags.collectAsState(initial = true).value
    val showInputProcessingStatus = preferencesManager.showInputProcessingStatus.collectAsState(initial = true).value

    // Collect avatar settings
    val userAvatarUri = preferencesManager.customUserAvatarUri.collectAsState(initial = null).value
    val aiAvatarUri = preferencesManager.customAiAvatarUri.collectAsState(initial = null).value
    val avatarShape = preferencesManager.avatarShape.collectAsState(initial = UserPreferencesManager.AVATAR_SHAPE_CIRCLE).value
    val avatarCornerRadius = preferencesManager.avatarCornerRadius.collectAsState(initial = 8f).value

    // Collect on color mode
    val onColorMode = preferencesManager.onColorMode.collectAsState(initial = UserPreferencesManager.ON_COLOR_MODE_AUTO).value

    // Collect recent colors
    val recentColors = preferencesManager.recentColorsFlow.collectAsState(initial = emptyList()).value



    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 自动保存主题到当前角色卡的函数
    val saveThemeToActiveCharacterCard: () -> Unit = {
        scope.launch {
            preferencesManager.saveCurrentThemeToCharacterCard(activeCharacterCard.id)
        }
    }

    // 包装的保存函数，会同时保存设置和角色卡主题
    fun saveThemeSettingsWithCharacterCard(saveAction: suspend () -> Unit) {
        scope.launch {
            saveAction()
            saveThemeToActiveCharacterCard()
        }
    }

    // Default color definitions
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()

    // Mutable state
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var primaryColorInput by remember { mutableStateOf(customPrimaryColor ?: defaultPrimaryColor) }
    var secondaryColorInput by remember {
        mutableStateOf(customSecondaryColor ?: defaultSecondaryColor)
    }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }

    // Background image state
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }

    // Background media type and video settings state
    var backgroundMediaTypeInput by remember { mutableStateOf(backgroundMediaType) }
    var videoBackgroundMutedInput by remember { mutableStateOf(videoBackgroundMuted) }
    var videoBackgroundLoopInput by remember { mutableStateOf(videoBackgroundLoop) }

    // Toolbar transparency state
    var toolbarTransparentInput by remember { mutableStateOf(toolbarTransparent) }
    
    // AppBar custom color state
    var useCustomAppBarColorInput by remember { mutableStateOf(useCustomAppBarColor) }
    var customAppBarColorInput by remember { mutableStateOf(customAppBarColor ?: defaultPrimaryColor) }

    // Status bar color state
    var useCustomStatusBarColorInput by remember { mutableStateOf(useCustomStatusBarColor) }
    var customStatusBarColorInput by remember { mutableStateOf(customStatusBarColor ?: defaultPrimaryColor) }
    var statusBarTransparentInput by remember { mutableStateOf(statusBarTransparent) }
    var statusBarHiddenInput by remember { mutableStateOf(statusBarHidden) }
    var chatHeaderTransparentInput by remember { mutableStateOf(chatHeaderTransparent) }
    var chatInputTransparentInput by remember { mutableStateOf(chatInputTransparent) }
    var chatHeaderOverlayModeInput by remember { mutableStateOf(chatHeaderOverlayMode) }

    // AppBar content color state
    var forceAppBarContentColorInput by remember { mutableStateOf(forceAppBarContentColor) }
    var appBarContentColorModeInput by remember { mutableStateOf(appBarContentColorMode) }

    // ChatHeader icon color state
    var chatHeaderHistoryIconColorInput by remember {
        mutableStateOf(chatHeaderHistoryIconColor ?: Color.Gray.toArgb())
    }
    var chatHeaderPipIconColorInput by remember {
        mutableStateOf(chatHeaderPipIconColor ?: Color.Gray.toArgb())
    }

    // Background blur state
    var useBackgroundBlurInput by remember { mutableStateOf(useBackgroundBlur) }
    var backgroundBlurRadiusInput by remember { mutableStateOf(backgroundBlurRadius) }

    // Chat style state
    var chatStyleInput by remember { mutableStateOf(chatStyle) }

    // New display settings state
    var showThinkingProcessInput by remember { mutableStateOf(showThinkingProcess) }
    var showStatusTagsInput by remember { mutableStateOf(showStatusTags) }
    var showInputProcessingStatusInput by remember { mutableStateOf(showInputProcessingStatus) }

    // Avatar state
    var userAvatarUriInput by remember { mutableStateOf(userAvatarUri) }
    var aiAvatarUriInput by remember { mutableStateOf(aiAvatarUri) }
    var avatarShapeInput by remember { mutableStateOf(avatarShape) }
    var avatarCornerRadiusInput by remember { mutableStateOf(avatarCornerRadius) }

    // 添加全局用户头像状态（已迁移到DisplayPreferencesManager）
    val globalUserAvatarUri = displayPreferencesManager.globalUserAvatarUri.collectAsState(initial = null).value
    var globalUserAvatarUriInput by remember { mutableStateOf(globalUserAvatarUri) }

    // 添加全局用户名称状态（已迁移到DisplayPreferencesManager）
    val globalUserName = displayPreferencesManager.globalUserName.collectAsState(initial = null).value
    var globalUserNameInput by remember { mutableStateOf(globalUserName) }

    // On color mode state
    var onColorModeInput by remember { mutableStateOf(onColorMode) }

    // 字体设置状态
    val useCustomFont = preferencesManager.useCustomFont.collectAsState(initial = false).value
    val fontType = preferencesManager.fontType.collectAsState(initial = UserPreferencesManager.FONT_TYPE_SYSTEM).value
    val systemFontName = preferencesManager.systemFontName.collectAsState(initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT).value
    val customFontPath = preferencesManager.customFontPath.collectAsState(initial = null).value
    val fontScale = preferencesManager.fontScale.collectAsState(initial = 1.0f).value

    var useCustomFontInput by remember { mutableStateOf(useCustomFont) }
    var fontTypeInput by remember { mutableStateOf(fontType) }
    var systemFontNameInput by remember { mutableStateOf(systemFontName) }
    var customFontPathInput by remember { mutableStateOf(customFontPath) }
    var fontScaleInput by remember { mutableStateOf(fontScale) }

    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }

    // Video player state
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
                // Add stricter memory limits
                .setLoadControl(
                        DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        5000, // Minimum buffer time reduced to 5 seconds
                                        10000, // Maximum buffer time reduced to 10 seconds
                                        500, // Minimum buffer for playback
                                        1000 // Minimum buffer for playback after rebuffering
                                )
                                .setTargetBufferBytes(5 * 1024 * 1024) // Limit buffer to 5MB
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()
                )
                .build()
                .apply {
                    // Set loop playback
                    repeatMode = Player.REPEAT_MODE_ALL
                    // Set mute
                    volume = if (videoBackgroundMutedInput) 0f else 1f
                    playWhenReady = true

                    // If there's a background video URI, load it
                    if (!backgroundImageUriInput.isNullOrEmpty() &&
                                    backgroundMediaTypeInput ==
                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                    ) {
                        try {
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(backgroundImageUriInput))
                                .build()
                            setMediaItem(mediaItem)
                            prepare()
                        } catch (e: Exception) {
                            AppLogger.e("ThemeSettings", "Video loading error", e)
                        }
                    }
                }
    }

    // Free ExoPlayer resources when component is destroyed
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "ExoPlayer release error", e)
            }
        }
    }

    // Handle video URI changes
    LaunchedEffect(backgroundImageUriInput, backgroundMediaTypeInput) {
        if (!backgroundImageUriInput.isNullOrEmpty() &&
                        backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
        ) {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(MediaItem.Builder()
                    .setUri(Uri.parse(backgroundImageUriInput))
                    .build())
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "更新视频来源错误", e)
            }
        }
    }

    // Handle video settings changes - add error handling
    LaunchedEffect(videoBackgroundMutedInput, videoBackgroundLoopInput) {
        try {
            exoPlayer.volume = if (videoBackgroundMutedInput) 0f else 1f
            exoPlayer.repeatMode =
                    if (videoBackgroundLoopInput) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        } catch (e: Exception) {
            AppLogger.e("ThemeSettings", "更新视频设置错误", e)
        }
    }

    // Image crop launcher
    val cropImageLauncher =
            rememberLauncherForActivityResult(CropImageContract()) { result ->
                if (result.isSuccessful) {
                    val croppedUri = result.uriContent
                    if (croppedUri != null) {
                        scope.launch {
                            val internalUri =
                                    FileUtils.copyFileToInternalStorage(context, croppedUri, "background")
                            if (internalUri != null) {
                                AppLogger.d("ThemeSettings", "Background image saved to: $internalUri")
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_IMAGE
                                    )
                                }
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_image_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
                } else if (result.error != null) {
                    Toast.makeText(
                                    context,
                                    context.getString(
                                            R.string.theme_image_crop_failed,
                                            result.error!!.message
                                    ),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }

    // Launch image crop function
    fun launchImageCrop(uri: Uri) {
        // Use safe way to get system colors
        var primaryColor: Int
        var onPrimaryColor: Int
        var surfaceColor: Int
        var statusBarColor: Int

        // Check system dark theme
        val isNightMode =
                context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            // Try to use theme colors - this is a fallback option
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            primaryColor = typedValue.data

            // Try to get system status bar color (API 23+)
            try {
                context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
                statusBarColor = typedValue.data
            } catch (e: Exception) {
                // If unable to get, use theme color
                statusBarColor = primaryColor
            }

            context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            surfaceColor = typedValue.data

            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } catch (e: Exception) {
            // Use fallback colors
            primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt() // Purple
            statusBarColor =
                    if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt() // Dark purple
            surfaceColor =
                    if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        val cropOptions =
                CropImageContractOptions(
                        uri,
                        CropImageOptions().apply {
                            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                            outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                            outputCompressQuality = 90
                            fixAspectRatio = false
                            cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                            activityTitle = context.getString(R.string.theme_crop_image)

                            // Set theme colors
                            toolbarColor = primaryColor
                            toolbarBackButtonColor = onPrimaryColor
                            toolbarTitleColor = onPrimaryColor
                            activityBackgroundColor = surfaceColor
                            backgroundColor = surfaceColor

                            // Status bar color
                            statusBarColor = statusBarColor

                            // Use light/dark theme
                            activityMenuIconColor = onPrimaryColor

                            // Improve user experience
                            showCropOverlay = true
                            showProgressBar = true
                            multiTouchEnabled = true
                            autoZoomEnabled = true
                        }
                )
        cropImageLauncher.launch(cropOptions)
    }

    // Image/video picker launcher
    val mediaPickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                if (uri != null) {
                    // Check if it's a video file
                    val isVideo = FileUtils.isVideoFile(context, uri)

                    if (isVideo) {
                        // Video file check size
                        val isVideoSizeAcceptable =
                                FileUtils.checkVideoSize(context, uri, 30) // Limit to 30MB

                        if (!isVideoSizeAcceptable) {
                            // Video too large, show warning
                            Toast.makeText(
                                            context,
                                            context.getString(R.string.theme_video_too_large),
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                            return@rememberLauncherForActivityResult
                        }

                        // Video file size acceptable, directly save
                        scope.launch {
                            val internalUri = FileUtils.copyFileToInternalStorage(context, uri, "background_video")

                            if (internalUri != null) {
                                AppLogger.d("ThemeSettings", "Background video saved to: $internalUri")
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_VIDEO
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                    )
                                }
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_video_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    } else {
                        // Image file first launch crop
                        launchImageCrop(uri)
                    }
                }
            }

    // Migrate existing background image if needed (on first load)
    LaunchedEffect(Unit) {
        // Check if we have a background image URI that starts with content://
        backgroundImageUri?.let { uriString ->
            if (uriString.startsWith("content://")) {
                try {
                    // Try to copy to internal storage
                    val uri = Uri.parse(uriString)
                    scope.launch {
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri, "migrated_background")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Migrated background image to: $internalUri")
                            // Update the URI in preferences
                            preferencesManager.saveThemeSettings(
                                    backgroundImageUri = internalUri.toString()
                            )
                            // Update the local state
                            backgroundImageUriInput = internalUri.toString()
                            Toast.makeText(context, context.getString(R.string.background_image_migrated), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("ThemeSettings", "Error migrating background image", e)
                    // If migration fails, disable background image to prevent
                    // crashes
                    scope.launch {
                        preferencesManager.saveThemeSettings(useBackgroundImage = false)
                        useBackgroundImageInput = false
                        Toast.makeText(context, context.getString(R.string.background_image_access_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // When settings change, update local state
    LaunchedEffect(
            themeMode,
            useSystemTheme,
            customPrimaryColor,
            customSecondaryColor,
            useCustomColors,
            useBackgroundImage,
            backgroundImageUri,
            backgroundImageOpacity,
            backgroundMediaType,
            videoBackgroundMuted,
            videoBackgroundLoop,
            toolbarTransparent,
            useCustomStatusBarColor,
            customStatusBarColor,
            statusBarTransparent,
            statusBarHidden,
            chatHeaderTransparent,
            chatInputTransparent,
            chatHeaderOverlayMode,
            forceAppBarContentColor,
            appBarContentColorMode,
            chatHeaderHistoryIconColor,
            chatHeaderPipIconColor,
            useBackgroundBlur,
            backgroundBlurRadius,
            chatStyle,
            showThinkingProcess,
            showStatusTags,
            showInputProcessingStatus,
            userAvatarUri,
            aiAvatarUri,
            avatarShape,
            avatarCornerRadius,
            onColorMode,
            globalUserAvatarUri,
            globalUserName,
            useCustomFont,
            fontType,
            systemFontName,
            customFontPath,
            fontScale
    ) {
        themeModeInput = themeMode
        useSystemThemeInput = useSystemTheme
        if (customPrimaryColor != null) primaryColorInput = customPrimaryColor
        if (customSecondaryColor != null) secondaryColorInput = customSecondaryColor
        useCustomColorsInput = useCustomColors
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
        backgroundMediaTypeInput = backgroundMediaType
        videoBackgroundMutedInput = videoBackgroundMuted
        videoBackgroundLoopInput = videoBackgroundLoop
        toolbarTransparentInput = toolbarTransparent
        useCustomStatusBarColorInput = useCustomStatusBarColor
        if (customStatusBarColor != null) customStatusBarColorInput = customStatusBarColor
        statusBarTransparentInput = statusBarTransparent
        statusBarHiddenInput = statusBarHidden
        chatHeaderTransparentInput = chatHeaderTransparent
        chatInputTransparentInput = chatInputTransparent
        chatHeaderOverlayModeInput = chatHeaderOverlayMode
        forceAppBarContentColorInput = forceAppBarContentColor
        appBarContentColorModeInput = appBarContentColorMode
        if (chatHeaderHistoryIconColor != null) {
            chatHeaderHistoryIconColorInput = chatHeaderHistoryIconColor
        }
        if (chatHeaderPipIconColor != null) {
            chatHeaderPipIconColorInput = chatHeaderPipIconColor
        }
        useBackgroundBlurInput = useBackgroundBlur
        backgroundBlurRadiusInput = backgroundBlurRadius
        chatStyleInput = chatStyle
        showThinkingProcessInput = showThinkingProcess
        showStatusTagsInput = showStatusTags
        showInputProcessingStatusInput = showInputProcessingStatus
        userAvatarUriInput = userAvatarUri
        aiAvatarUriInput = aiAvatarUri
        avatarShapeInput = avatarShape
        avatarCornerRadiusInput = avatarCornerRadius
        onColorModeInput = onColorMode
        globalUserAvatarUriInput = globalUserAvatarUri
        globalUserNameInput = globalUserName
        useCustomFontInput = useCustomFont
        fontTypeInput = fontType
        systemFontNameInput = systemFontName
        customFontPathInput = customFontPath
        fontScaleInput = fontScale
    }

    // 字体文件选择器 launcher
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    // 获取文件扩展名进行验证
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    
                    // 检查是否是支持的字体文件格式
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        // 复制字体文件到内部存储
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            customFontPathInput = internalUri.toString()
                            fontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    customFontPath = internalUri.toString(),
                                    fontType = UserPreferencesManager.FONT_TYPE_FILE
                                )
                            }
                            Toast.makeText(context, context.getString(R.string.font_file_saved, extension), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.font_file_save_failed), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // 不是支持的字体格式
                        Toast.makeText(
                            context, 
                            context.getString(R.string.unsupported_font_format),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    // Avatar picker and cropper launcher
    var avatarPickerMode by remember { mutableStateOf("user") }

    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    val uniqueName = when (avatarPickerMode) {
                        "user" -> "user_avatar"
                        "ai" -> "ai_avatar"
                        "global_user" -> "global_user_avatar"
                        else -> "user_avatar"
                    }
                    val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, uniqueName)
                    if (internalUri != null) {
                        when (avatarPickerMode) {
                            "user" -> {
                                AppLogger.d("ThemeSettings", "User avatar saved to: $internalUri")
                                userAvatarUriInput = internalUri.toString()
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(customUserAvatarUri = internalUri.toString())
                                }
                            }
                            "ai" -> {
                                AppLogger.d("ThemeSettings", "AI avatar saved to: $internalUri")
                                aiAvatarUriInput = internalUri.toString()
                                preferencesManager.saveThemeSettings(customAiAvatarUri = internalUri.toString())
                            }
                            "global_user" -> {
                                AppLogger.d("ThemeSettings", "Global user avatar saved to: $internalUri")
                                globalUserAvatarUriInput = internalUri.toString()
                                displayPreferencesManager.saveDisplaySettings(globalUserAvatarUri = internalUri.toString())
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = "裁剪头像"
                // Basic theming, can be expanded later
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchAvatarCrop(uri)
        }
    }


    // Get background image state to check if we need opaque cards
    val hasBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value

    // Color surface modifier based on whether background image is used
    val cardModifier =
            if (hasBackgroundImage) {
                // Make cards fully opaque when background image is used
                CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)
                )
            } else {
                CardDefaults.cardColors()
            }

    // Add a scroll state that we can control
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        // ======= 角色卡主题绑定信息 =======
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = cardModifier,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 显示头像但不可编辑
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (aiAvatarUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(Uri.parse(aiAvatarUri)),
                            contentDescription = "角色头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "默认头像",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.current_character, activeCharacterCard.name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = context.getString(R.string.theme_auto_bind_character_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    Icons.Default.Link,
                    contentDescription = "绑定",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ======= SECTION 1: THEME MODE =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_mode),
                icon = Icons.Default.Brightness4
        )

        // System theme settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_system_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Follow system theme
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_follow_system),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_follow_system_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useSystemThemeInput,
                            onCheckedChange = {
                                useSystemThemeInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(useSystemTheme = it)
                                }
                            }
                    )
                }

                // Only show theme selection when not following system
                if (!useSystemThemeInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                            text = stringResource(id = R.string.theme_select),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Theme mode selection
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_light),
                                selected =
                                        themeModeInput == UserPreferencesManager.THEME_MODE_LIGHT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_LIGHT
                                        )
                                    }
                                }
                        )

                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_dark),
                                selected = themeModeInput == UserPreferencesManager.THEME_MODE_DARK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_DARK
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_DARK
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }

        // ======= SECTION 2: COLOR CUSTOMIZATION =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_color),
                icon = Icons.Default.ColorLens
        )

        // Add status bar color settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_statusbar_color),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Status bar hidden switch
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_statusbar_hidden),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_statusbar_hidden_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = statusBarHiddenInput,
                            onCheckedChange = {
                                statusBarHiddenInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(statusBarHidden = it)
                                }
                            }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Status bar transparent switch
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_statusbar_transparent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (statusBarHiddenInput) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                text = stringResource(id = R.string.theme_statusbar_transparent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusBarHiddenInput) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = statusBarTransparentInput,
                            enabled = !statusBarHiddenInput,
                            onCheckedChange = {
                                statusBarTransparentInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(statusBarTransparent = it)
                                }
                            }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Use custom status bar color switch
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_statusbar_color),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (statusBarTransparentInput || statusBarHiddenInput) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_statusbar_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusBarTransparentInput || statusBarHiddenInput) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = useCustomStatusBarColorInput,
                            enabled = !statusBarTransparentInput && !statusBarHiddenInput,
                            onCheckedChange = {
                                useCustomStatusBarColorInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(useCustomStatusBarColor = it)
                                }
                            }
                    )
                }

                if (useCustomStatusBarColorInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // Status bar color selection
                    ColorSelectionItem(
                            title = stringResource(id = R.string.theme_statusbar_color),
                            color = Color(customStatusBarColorInput),
                            enabled = !statusBarTransparentInput && !statusBarHiddenInput,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (!statusBarTransparentInput && !statusBarHiddenInput) {
                                    currentColorPickerMode = "statusBar"
                                    showColorPicker = true
                                }
                            }
                    )
                }
            }
        }


        // Add toolbar transparency settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_toolbar_transparent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Toolbar transparency
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.theme_toolbar_transparent_desc), style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = stringResource(id = R.string.theme_toolbar_transparent_desc_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = toolbarTransparentInput,
                            onCheckedChange = {
                                toolbarTransparentInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(toolbarTransparent = it)
                                }
                            }
                    )
                }
                
                // Custom AppBar Color
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_appbar_color),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_appbar_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = useCustomAppBarColorInput,
                            enabled = !toolbarTransparentInput,
                            onCheckedChange = {
                                useCustomAppBarColorInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(useCustomAppBarColor = it)
                                }
                            }
                    )
                }
                
                // AppBar Color Picker
                if (useCustomAppBarColorInput && !toolbarTransparentInput) {
                    ColorSelectionItem(
                            title = stringResource(id = R.string.theme_appbar_color),
                            color = Color(customAppBarColorInput),
                            enabled = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                currentColorPickerMode = "appBar"
                                showColorPicker = true
                            }
                    )
                }
            }
        }

        // Chat header transparency
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_chat_header_transparent_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.theme_chat_header_transparent), style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = stringResource(id = R.string.theme_chat_header_transparent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = chatHeaderTransparentInput,
                            onCheckedChange = {
                                chatHeaderTransparentInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(chatHeaderTransparent = it)
                                }
                            }
                    )
                }

                if (chatHeaderTransparentInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = stringResource(id = R.string.theme_chat_header_overlay_mode),
                                    style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                    text = stringResource(id = R.string.theme_chat_header_overlay_mode_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                                checked = chatHeaderOverlayModeInput,
                                onCheckedChange = {
                                    chatHeaderOverlayModeInput = it
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                                chatHeaderOverlayMode = it
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }

        // Chat input transparency
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_chat_input_transparent_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.theme_chat_input_transparent), style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = stringResource(id = R.string.theme_chat_input_transparent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = chatInputTransparentInput,
                            onCheckedChange = {
                                chatInputTransparentInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(chatInputTransparent = it)
                                }
                            }
                    )
                }
            }
        }

        // Add AppBar content color settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_appbar_content_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Force AppBar content color
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_force_appbar_content_color),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_force_appbar_content_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = forceAppBarContentColorInput,
                            onCheckedChange = {
                                forceAppBarContentColorInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(forceAppBarContentColor = it)
                                }
                            }
                    )
                }

                if (forceAppBarContentColorInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                            text = stringResource(id = R.string.theme_appbar_content_color_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_appbar_content_color_light),
                                selected = appBarContentColorModeInput == UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    appBarContentColorModeInput = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(appBarContentColorMode = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT)
                                    }
                                }
                        )
                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_appbar_content_color_dark),
                                selected = appBarContentColorModeInput == UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    appBarContentColorModeInput = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(appBarContentColorMode = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK)
                                    }
                                }
                        )
                    }
                }
            }
        }

        // ChatHeader icon color settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_chat_header_icons_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
                ColorSelectionItem(
                        title = stringResource(id = R.string.theme_chat_header_history_icon_color),
                        color = Color(chatHeaderHistoryIconColorInput),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            currentColorPickerMode = "historyIcon"
                            showColorPicker = true
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorSelectionItem(
                        title = stringResource(id = R.string.theme_chat_header_pip_icon_color),
                        color = Color(chatHeaderPipIconColorInput),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            currentColorPickerMode = "pipIcon"
                            showColorPicker = true
                        }
                )
            }
        }

        // Custom color settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_custom_color),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Whether to use custom colors
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_color),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_custom_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useCustomColorsInput,
                            onCheckedChange = {
                                useCustomColorsInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(useCustomColors = it)
                                }
                            }
                    )
                }

                // Only show color selection when custom colors are enabled
                if (useCustomColorsInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                            text = stringResource(id = R.string.theme_select_color),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Color selection
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Primary color selection
                        ColorSelectionItem(
                                title = stringResource(id = R.string.theme_primary_color),
                                color = Color(primaryColorInput),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentColorPickerMode = "primary"
                                    showColorPicker = true
                                }
                        )

                        // Secondary color selection
                        ColorSelectionItem(
                                title = stringResource(id = R.string.theme_secondary_color),
                                color = Color(secondaryColorInput),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentColorPickerMode = "secondary"
                                    showColorPicker = true
                                }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(id = R.string.theme_on_color_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                            title = stringResource(id = R.string.theme_on_color_auto),
                            selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_AUTO,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onColorModeInput = UserPreferencesManager.ON_COLOR_MODE_AUTO
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(onColorMode = UserPreferencesManager.ON_COLOR_MODE_AUTO)
                                }
                            }
                        )
                        ThemeModeOption(
                            title = stringResource(id = R.string.theme_on_color_light),
                            selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onColorModeInput = UserPreferencesManager.ON_COLOR_MODE_LIGHT
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(onColorMode = UserPreferencesManager.ON_COLOR_MODE_LIGHT)
                                }
                            }
                        )
                        ThemeModeOption(
                            title = stringResource(id = R.string.theme_on_color_dark),
                            selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_DARK,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onColorModeInput = UserPreferencesManager.ON_COLOR_MODE_DARK
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(onColorMode = UserPreferencesManager.ON_COLOR_MODE_DARK)
                                }
                                }
                        )
                    }

                    // Add a color preview section to show how colors will look
                    Text(
                            text = stringResource(id = R.string.theme_preview),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Create a mini-preview of how the selected colors will look
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        // Primary color demo
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            val primaryColor = Color(primaryColorInput)
                            val onPrimaryColor = getTextColorForBackground(primaryColor)

                            // Primary button preview
                            Surface(
                                    modifier =
                                            Modifier.weight(1f).height(40.dp).padding(end = 8.dp),
                                    color = primaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            stringResource(id = R.string.theme_primary_button),
                                            color = onPrimaryColor,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Secondary button preview
                            val secondaryColor = Color(secondaryColorInput)
                            val onSecondaryColor = getTextColorForBackground(secondaryColor)

                            Surface(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    color = secondaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            stringResource(id = R.string.theme_secondary_button),
                                            color = onSecondaryColor,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Text(
                                text = stringResource(id = R.string.theme_contrast_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Save custom colors button
                    Button(
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveThemeSettings(
                                            customPrimaryColor = primaryColorInput,
                                            customSecondaryColor = secondaryColorInput
                                    )
                                    showSaveSuccessMessage = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { Text(stringResource(id = R.string.theme_save_colors)) }
                }
            }
        }

        // ======= SECTION 2: CHAT STYLE =======
        ThemeSectionTitle(
            title = stringResource(id = R.string.chat_style_title),
            icon = Icons.Default.ColorLens // 您可以根据需要更改图标
        )

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.chat_style_desc),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatStyleOption(
                        title = stringResource(id = R.string.chat_style_cursor),
                        selected = chatStyleInput == UserPreferencesManager.CHAT_STYLE_CURSOR,
                        modifier = Modifier.weight(1f)
                    ) {
                        chatStyleInput = UserPreferencesManager.CHAT_STYLE_CURSOR
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(chatStyle = UserPreferencesManager.CHAT_STYLE_CURSOR)
                        }
                    }

                    ChatStyleOption(
                        title = stringResource(id = R.string.chat_style_bubble),
                        selected = chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE,
                        modifier = Modifier.weight(1f)
                    ) {
                        chatStyleInput = UserPreferencesManager.CHAT_STYLE_BUBBLE
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(chatStyle = UserPreferencesManager.CHAT_STYLE_BUBBLE)
                        }
                    }
                }
            }
        }

        // ======= SECTION 4: DISPLAY OPTIONS =======
        ThemeSectionTitle(
            title = stringResource(id = R.string.display_options_title),
            icon = Icons.Default.ColorLens // Replace with a more appropriate icon if available
        )
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Show thinking process switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.show_thinking_process), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(id = R.string.show_thinking_process_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showThinkingProcessInput,
                        onCheckedChange = {
                            showThinkingProcessInput = it
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(showThinkingProcess = it)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Show status tags switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.show_status_tags), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(id = R.string.show_status_tags_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showStatusTagsInput,
                        onCheckedChange = {
                            showStatusTagsInput = it
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(showStatusTags = it)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Show input processing status switch
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.show_input_processing_status), style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = stringResource(id = R.string.show_input_processing_status_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = showInputProcessingStatusInput,
                            onCheckedChange = {
                                showInputProcessingStatusInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(showInputProcessingStatus = it)
                                }
                            }
                    )
                }
            }
        }

        // ======= SECTION: FONT SETTINGS =======
        ThemeSectionTitle(
            title = "字体设置",
            icon = Icons.Default.TextFields
        )

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 启用自定义字体开关
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.enable_custom_font),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = context.getString(R.string.use_system_or_custom_font),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCustomFontInput,
                        onCheckedChange = {
                            useCustomFontInput = it
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(useCustomFont = it)
                            }
                        }
                    )
                }

                // 字体设置只在启用自定义字体时显示
                if (useCustomFontInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 字体类型选择
                    Text(
                        text = context.getString(R.string.font_type_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 系统字体按钮
                        FilterChip(
                            selected = fontTypeInput == UserPreferencesManager.FONT_TYPE_SYSTEM,
                            onClick = {
                                fontTypeInput = UserPreferencesManager.FONT_TYPE_SYSTEM
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        fontType = UserPreferencesManager.FONT_TYPE_SYSTEM
                                    )
                                }
                            },
                            label = { Text(context.getString(R.string.system_font)) }
                        )

                        // 自定义文件按钮
                        FilterChip(
                            selected = fontTypeInput == UserPreferencesManager.FONT_TYPE_FILE,
                            onClick = {
                                fontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                        fontType = UserPreferencesManager.FONT_TYPE_FILE
                                    )
                                }
                            },
                            label = { Text(context.getString(R.string.custom_font_file)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 根据选择的类型显示不同的设置
                    when (fontTypeInput) {
                        UserPreferencesManager.FONT_TYPE_SYSTEM -> {
                            // 系统字体选择
                            Text(
                                text = context.getString(R.string.select_system_font),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    UserPreferencesManager.SYSTEM_FONT_DEFAULT to "默认字体",
                                    UserPreferencesManager.SYSTEM_FONT_SERIF to "衬线体 (Serif)",
                                    UserPreferencesManager.SYSTEM_FONT_SANS_SERIF to "无衬线体 (Sans Serif)",
                                    UserPreferencesManager.SYSTEM_FONT_MONOSPACE to "等宽字体 (Monospace)",
                                    UserPreferencesManager.SYSTEM_FONT_CURSIVE to "手写体 (Cursive)"
                                ).forEach { (fontName, displayName) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = systemFontNameInput == fontName,
                                            onClick = {
                                                systemFontNameInput = fontName
                                                saveThemeSettingsWithCharacterCard {
                                                    preferencesManager.saveThemeSettings(
                                                        systemFontName = fontName
                                                    )
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        UserPreferencesManager.FONT_TYPE_FILE -> {
                            // 自定义字体文件
                            Text(
                                text = context.getString(R.string.custom_font_file_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = context.getString(R.string.font_file_support_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        // 使用 */* 让用户可以选择任意文件，包括 .ttf 和 .otf 字体文件
                                        fontPickerLauncher.launch("*/*")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.select_font_file))
                                }

                                if (!customFontPathInput.isNullOrEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            customFontPathInput = null
                                            saveThemeSettingsWithCharacterCard {
                                                preferencesManager.saveThemeSettings(
                                                    customFontPath = ""
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(context.getString(R.string.clear_font))
                                    }
                                }
                            }

                            if (!customFontPathInput.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.current_font_file_path, customFontPathInput?.substringAfterLast("/") ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // 添加字体大小调整滑块
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = context.getString(R.string.font_size_scale_label, String.format("%.1f", fontScaleInput)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = fontScaleInput,
                        onValueChange = { fontScaleInput = it },
                        onValueChangeFinished = {
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(fontScale = fontScaleInput)
                            }
                        },
                        valueRange = 0.8f..1.5f,
                        steps = 6
                    )
                }
            }
        }

        // ======= SECTION 5: AVATAR CUSTOMIZATION =======
        ThemeSectionTitle(
            title = stringResource(id = R.string.avatar_customization_title),
            icon = Icons.Default.Person // Replace with a more appropriate icon if available
        )
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                // User Avatar Pickers - 横向排列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // User Avatar Picker
                    AvatarPicker(
                        label = stringResource(id = R.string.user_avatar_label),
                        avatarUri = userAvatarUriInput,
                        onAvatarChange = {
                            avatarPickerMode = "user"
                            avatarImagePicker.launch("image/*")
                        },
                        onAvatarReset = {
                            userAvatarUriInput = null
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(customUserAvatarUri = "")
                            }
                        }
                    )

                    // Global User Avatar Picker
                    AvatarPicker(
                        label = stringResource(id = R.string.global_user_avatar_label),
                        avatarUri = globalUserAvatarUriInput,
                        onAvatarChange = {
                            avatarPickerMode = "global_user"
                            avatarImagePicker.launch("image/*")
                        },
                        onAvatarReset = {
                            globalUserAvatarUriInput = null
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(globalUserAvatarUri = "")
                            }
                        }
                    )
                }

                // 添加说明文字
                Text(
                    text = "说明：左侧的用户头像是角色卡专属设置，为空时将使用右侧的全局用户头像。全局用户头像在所有角色卡中共享使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 全局用户名称设置
                Text(
                    text = stringResource(id = R.string.global_user_name_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = globalUserNameInput ?: "",
                    onValueChange = { globalUserNameInput = it },
                    label = { Text(stringResource(id = R.string.global_user_name_label)) },
                    placeholder = { Text(stringResource(id = R.string.global_user_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (globalUserNameInput.isNullOrEmpty()) {
                            IconButton(
                                onClick = {
                                    globalUserNameInput = ""
                                    scope.launch {
                                        displayPreferencesManager.saveDisplaySettings(globalUserName = "")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(id = R.string.clear_action))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        displayPreferencesManager.saveDisplaySettings(globalUserName = globalUserNameInput)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save_action))
                            }
                        }
                    }
                )

                Text(
                    text = stringResource(id = R.string.global_user_name_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.avatar_shape_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatStyleOption(
                        title = stringResource(id = R.string.avatar_shape_circle),
                        selected = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_CIRCLE,
                        modifier = Modifier.weight(1f)
                    ) {
                        avatarShapeInput = UserPreferencesManager.AVATAR_SHAPE_CIRCLE
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(avatarShape = UserPreferencesManager.AVATAR_SHAPE_CIRCLE)
                        }
                    }
                    ChatStyleOption(
                        title = stringResource(id = R.string.avatar_shape_square),
                        selected = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_SQUARE,
                        modifier = Modifier.weight(1f)
                    ) {
                        avatarShapeInput = UserPreferencesManager.AVATAR_SHAPE_SQUARE
                        saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(avatarShape = UserPreferencesManager.AVATAR_SHAPE_SQUARE)
                        }
                    }
                }

                AnimatedVisibility(visible = avatarShapeInput == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = stringResource(id = R.string.avatar_corner_radius),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val newValue = (avatarCornerRadiusInput - 1f).coerceIn(0f, 16f)
                                    avatarCornerRadiusInput = newValue
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(avatarCornerRadius = newValue)
                                    }
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = stringResource(id = R.string.avatar_corner_decrease))
                            }

                            Text(
                                text = "${avatarCornerRadiusInput.toInt()} dp",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            OutlinedButton(
                                onClick = {
                                    val newValue = (avatarCornerRadiusInput + 1f).coerceIn(0f, 16f)
                                    avatarCornerRadiusInput = newValue
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(avatarCornerRadius = newValue)
                                    }
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.avatar_corner_increase))
                            }
                        }
                    }
                }
            }
        }


        // ======= SECTION 3: BACKGROUND CUSTOMIZATION =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_background),
                icon = Icons.Default.Image
        )

        // Background media settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_bg_media),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Whether to use background image
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_bg),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_custom_bg_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useBackgroundImageInput,
                            onCheckedChange = {
                                useBackgroundImageInput = it
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(useBackgroundImage = it)
                                }
                            }
                    )
                }

                // Only show image selection when background image is enabled
                if (useBackgroundImageInput) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Media type selection
                    Text(
                            text = stringResource(id = R.string.theme_media_type),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaTypeOption(
                                title = stringResource(id = R.string.theme_media_image),
                                icon = Icons.Default.Image,
                                selected =
                                        backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_IMAGE,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    backgroundMediaTypeInput =
                                            UserPreferencesManager.MEDIA_TYPE_IMAGE
                                    if (!backgroundImageUriInput.isNullOrEmpty()) {
                                        // If there's already a background, save the media type
                                        saveThemeSettingsWithCharacterCard {
                                            preferencesManager.saveThemeSettings(
                                                    backgroundMediaType =
                                                            UserPreferencesManager.MEDIA_TYPE_IMAGE
                                            )
                                        }
                                    }
                                }
                        )

                        MediaTypeOption(
                                title = stringResource(id = R.string.theme_media_video),
                                icon = Icons.Default.Videocam,
                                selected =
                                        backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    backgroundMediaTypeInput =
                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                                    if (!backgroundImageUriInput.isNullOrEmpty()) {
                                        // If there's already a background, save the media type
                                        saveThemeSettingsWithCharacterCard {
                                            preferencesManager.saveThemeSettings(
                                                    backgroundMediaType =
                                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                                            )
                                        }
                                    }
                                }
                        )
                    }

                    // Current selected media preview
                    if (!backgroundImageUriInput.isNullOrEmpty()) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(200.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(Color.Black.copy(alpha = 0.1f))
                        ) {
                            if (backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_IMAGE
                            ) {
                                // Image preview
                                Image(
                                        painter =
                                                rememberAsyncImagePainter(
                                                        Uri.parse(backgroundImageUriInput)
                                                ),
                                        contentDescription = "背景图片预览",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                )

                                // Crop button
                                IconButton(
                                        onClick = {
                                            backgroundImageUriInput?.let {
                                                launchImageCrop(Uri.parse(it))
                                            }
                                        },
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f),
                                                                CircleShape
                                                        )
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = "重新裁剪",
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                // Video preview
                                // Capture the background color from the Composable context
                                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                                // Determine if it's a light theme
                                val isLightTheme =
                                        calculateLuminance(MaterialTheme.colorScheme.background) >
                                                0.5f

                                AndroidView(
                                        factory = { ctx ->
                                            StyledPlayerView(ctx).apply {
                                                player = exoPlayer
                                                useController = false
                                                layoutParams =
                                                        ViewGroup.LayoutParams(
                                                                MATCH_PARENT,
                                                                MATCH_PARENT
                                                        )
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                // Use the captured background color
                                                setBackgroundColor(backgroundColor)
                                                // Create a semi-transparent overlay on the player
                                                // itself for opacity control
                                                foreground =
                                                        android.graphics.drawable.ColorDrawable(
                                                                android.graphics.Color.argb(
                                                                        ((1f -
                                                                                        backgroundImageOpacityInput) *
                                                                                        255)
                                                                                .toInt(),
                                                                        // Use white for light
                                                                        // theme, black for dark
                                                                        // theme
                                                                        if (isLightTheme) 255
                                                                        else 0,
                                                                        if (isLightTheme) 255
                                                                        else 0,
                                                                        if (isLightTheme) 255 else 0
                                                                )
                                                        )
                                            }
                                        },
                                        update = { view ->
                                            // Update the foreground transparency when opacity
                                            // changes
                                            view.foreground =
                                                    android.graphics.drawable.ColorDrawable(
                                                            android.graphics.Color.argb(
                                                                    ((1f -
                                                                                    backgroundImageOpacityInput) *
                                                                                    255)
                                                                            .toInt(),
                                                                    // Use white for light theme,
                                                                    // black for dark theme
                                                                    if (isLightTheme) 255 else 0,
                                                                    if (isLightTheme) 255 else 0,
                                                                    if (isLightTheme) 255 else 0
                                                            )
                                                    )
                                        },
                                        modifier = Modifier.fillMaxSize()
                                )

                                // Video control buttons
                                Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    // Mute button
                                    IconButton(
                                            onClick = {
                                                videoBackgroundMutedInput =
                                                        !videoBackgroundMutedInput
                                                saveThemeSettingsWithCharacterCard {
                                                    preferencesManager.saveThemeSettings(
                                                            videoBackgroundMuted =
                                                                    videoBackgroundMutedInput
                                                    )
                                                }
                                            },
                                            modifier =
                                                    Modifier.padding(end = 8.dp)
                                                            .background(
                                                                    MaterialTheme.colorScheme
                                                                            .surface.copy(
                                                                            alpha = 0.7f
                                                                    ),
                                                                    CircleShape
                                                            )
                                    ) {
                                        Icon(
                                                imageVector =
                                                        if (videoBackgroundMutedInput)
                                                                Icons.AutoMirrored.Outlined.VolumeOff
                                                        else Icons.AutoMirrored.Rounded.VolumeUp,
                                                contentDescription =
                                                        if (videoBackgroundMutedInput)
                                                                stringResource(
                                                                        id = R.string.theme_unmute
                                                                )
                                                        else
                                                                stringResource(
                                                                        id = R.string.theme_mute
                                                                ),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Loop button
                                    IconButton(
                                            onClick = {
                                                videoBackgroundLoopInput = !videoBackgroundLoopInput
                                                saveThemeSettingsWithCharacterCard {
                                                    preferencesManager.saveThemeSettings(
                                                            videoBackgroundLoop =
                                                                    videoBackgroundLoopInput
                                                    )

                                                    // Show Toast notification about status change
                                                    Toast.makeText(
                                                                    context,
                                                                    if (videoBackgroundLoopInput)
                                                                            context.getString(
                                                                                    R.string
                                                                                            .theme_loop_enabled
                                                                            )
                                                                    else
                                                                            context.getString(
                                                                                    R.string
                                                                                            .theme_loop_disabled
                                                                            ),
                                                                    Toast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                }
                                            },
                                            modifier =
                                                    Modifier.background(
                                                            // Show different background color based
                                                            // on loop state
                                                            if (videoBackgroundLoopInput)
                                                                    MaterialTheme.colorScheme
                                                                            .primary.copy(
                                                                            alpha = 0.3f
                                                                    )
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .surface.copy(
                                                                            alpha = 0.7f
                                                                    ),
                                                            CircleShape
                                                    )
                                    ) {
                                        Icon(
                                                if (videoBackgroundLoopInput) Icons.Default.Loop
                                                else Icons.Outlined.Loop,
                                                contentDescription =
                                                        if (videoBackgroundLoopInput)
                                                                stringResource(
                                                                        id = R.string.theme_loop_on
                                                                )
                                                        else
                                                                stringResource(
                                                                        id = R.string.theme_loop_off
                                                                ),
                                                tint =
                                                        if (videoBackgroundLoopInput)
                                                                MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(150.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                            Text(
                                    text = stringResource(id = R.string.theme_no_bg_selected),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                            onClick = {
                                if (backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                ) {
                                    mediaPickerLauncher.launch("video/*")
                                } else {
                                    mediaPickerLauncher.launch("image/*")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                                if (backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                )
                                        stringResource(id = R.string.theme_select_video)
                                else stringResource(id = R.string.theme_select_image)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opacity adjustment
                    Text(
                            text =
                                    stringResource(
                                            id = R.string.theme_bg_opacity,
                                            (backgroundImageOpacityInput * 100).toInt()
                                    ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Remember last saved value for debounce save operation
                    var lastSavedOpacity by remember { mutableStateOf(backgroundImageOpacityInput) }

                    // Use rememberUpdatedState to stabilize callback function
                    val currentScope = rememberCoroutineScope()

                    // Create a simpler disableScrollWhileDragging state
                    var isDragging by remember { mutableStateOf(false) }

                    // Custom interaction source, helps us monitor drag state
                    val interactionSource = remember { MutableInteractionSource() }

                    // Monitor drag state
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            when (interaction) {
                                is DragInteraction.Start -> isDragging = true
                                is DragInteraction.Stop -> isDragging = false
                                is DragInteraction.Cancel -> isDragging = false
                            }
                        }
                    }

                    // If in drag state, temporarily lock scrolling
                    if (isDragging) {
                        DisposableEffect(Unit) {
                            val previousScrollValue = scrollState.value
                            onDispose {
                                // Nothing to do on dispose
                            }
                        }
                    }

                    // Create a fixed update callback and finish callback
                    val updateOpacity = remember {
                        { value: Float -> backgroundImageOpacityInput = value }
                    }

                    val onValueChangeFinished = remember {
                        {
                            if (kotlin.math.abs(lastSavedOpacity - backgroundImageOpacityInput) >
                                            0.01f
                            ) {
                                saveThemeSettingsWithCharacterCard {
                                    preferencesManager.saveThemeSettings(
                                            backgroundImageOpacity = backgroundImageOpacityInput
                                    )
                                    lastSavedOpacity = backgroundImageOpacityInput
                                }
                            }
                        }
                    }

                    // Use Box to wrap slider, solve drag issue
                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp)) {
                        Slider(
                                value = backgroundImageOpacityInput,
                                onValueChange = updateOpacity,
                                onValueChangeFinished = onValueChangeFinished,
                                valueRange = 0.1f..1f,
                                interactionSource = interactionSource,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor =
                                                        MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        )
                        )
                    }

                    // Add a gap, ensure slider below has enough space
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Background blur settings
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = stringResource(id = R.string.theme_background_blur),
                                    style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                    text = stringResource(id = R.string.theme_background_blur_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                                checked = useBackgroundBlurInput,
                                onCheckedChange = {
                                    useBackgroundBlurInput = it
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(useBackgroundBlur = it)
                                    }
                                }
                        )
                    }

                    if (useBackgroundBlurInput) {
                        Text(
                                text =
                                        stringResource(id = R.string.theme_background_blur_radius) +
                                                ": ${backgroundBlurRadiusInput.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        )

                        // Remember last saved value for debounce save operation
                        var lastSavedBlurRadius by remember { mutableStateOf(backgroundBlurRadiusInput) }
                        val blurInteractionSource = remember { MutableInteractionSource() }

                        val onBlurValueChangeFinished = remember {
                            {
                                if (kotlin.math.abs(lastSavedBlurRadius - backgroundBlurRadiusInput) >
                                                0.1f
                                ) {
                                    saveThemeSettingsWithCharacterCard {
                                        preferencesManager.saveThemeSettings(
                                                backgroundBlurRadius = backgroundBlurRadiusInput
                                        )
                                        lastSavedBlurRadius = backgroundBlurRadiusInput
                                    }
                                }
                            }
                        }

                        // Use Box to wrap slider, solve drag issue
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp)
                        ) {
                            Slider(
                                    value = backgroundBlurRadiusInput,
                                    onValueChange = { backgroundBlurRadiusInput = it },
                                    onValueChangeFinished = onBlurValueChangeFinished,
                                    valueRange = 1f..30f,
                                    interactionSource = blurInteractionSource,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Reset button
        OutlinedButton(
                onClick = {
                    scope.launch {
                        preferencesManager.resetThemeSettings()
                        // 同时删除当前角色卡的主题配置
                        preferencesManager.deleteCharacterCardTheme(activeCharacterCard.id)
                        // Reset local state after reset
                        themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                        useSystemThemeInput = true
                        primaryColorInput = defaultPrimaryColor
                        secondaryColorInput = defaultSecondaryColor
                        useCustomColorsInput = false
                        useBackgroundImageInput = false
                        backgroundImageUriInput = null
                        backgroundImageOpacityInput = 0.3f
                        backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
                        videoBackgroundMutedInput = true
                        videoBackgroundLoopInput = true
                        toolbarTransparentInput = false
                        useCustomAppBarColorInput = false
                        customAppBarColorInput = defaultPrimaryColor
                        useCustomStatusBarColorInput = false
                        customStatusBarColorInput = defaultPrimaryColor
                        statusBarTransparentInput = false
                        statusBarHiddenInput = false
                        chatHeaderTransparentInput = false
                        chatInputTransparentInput = false
                        chatHeaderOverlayModeInput = false
                        forceAppBarContentColorInput = false
                        appBarContentColorModeInput = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                        chatHeaderHistoryIconColorInput = Color.Gray.toArgb()
                        chatHeaderPipIconColorInput = Color.Gray.toArgb()
                        useBackgroundBlurInput = false
                        backgroundBlurRadiusInput = 10f
                        chatStyleInput = UserPreferencesManager.CHAT_STYLE_CURSOR
                        showThinkingProcessInput = true
                        showStatusTagsInput = true
                        showInputProcessingStatusInput = true
                        userAvatarUriInput = null
                        aiAvatarUriInput = null
                        avatarShapeInput = UserPreferencesManager.AVATAR_SHAPE_CIRCLE
                        avatarCornerRadiusInput = 8f
                        onColorModeInput = UserPreferencesManager.ON_COLOR_MODE_AUTO
                        showSaveSuccessMessage = true
                        globalUserAvatarUriInput = null
                        globalUserNameInput = null
                        useCustomFontInput = false
                        fontTypeInput = UserPreferencesManager.FONT_TYPE_SYSTEM
                        systemFontNameInput = UserPreferencesManager.SYSTEM_FONT_DEFAULT
                        customFontPathInput = null
                        fontScaleInput = 1.0f
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) { Text(stringResource(id = R.string.theme_reset)) }

        // Show save success message
        if (showSaveSuccessMessage) {
            LaunchedEffect(key1 = showSaveSuccessMessage) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccessMessage = false
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                        text = stringResource(id = R.string.theme_saved),
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Color picker dialog
        if (showColorPicker) {
            ColorPickerDialog(
                    showColorPicker = showColorPicker,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColorInput = primaryColorInput,
                    secondaryColorInput = secondaryColorInput,
                    statusBarColorInput = customStatusBarColorInput,
                    appBarColorInput = customAppBarColorInput,
                    historyIconColorInput = chatHeaderHistoryIconColorInput,
                    pipIconColorInput = chatHeaderPipIconColorInput,
                    recentColors = recentColors,
                    onColorSelected = {
                        primaryColor,
                        secondaryColor,
                        statusBarColor,
                        appBarColor,
                        historyIconColor,
                        pipIconColor ->
                        primaryColor?.let { primaryColorInput = it }
                        secondaryColor?.let { secondaryColorInput = it }
                        statusBarColor?.let { customStatusBarColorInput = it }
                        appBarColor?.let { customAppBarColorInput = it }
                        historyIconColor?.let { chatHeaderHistoryIconColorInput = it }
                        pipIconColor?.let { chatHeaderPipIconColorInput = it }

                        // Save the new color to recent colors
                        val newColor =
                                primaryColor
                                        ?: secondaryColor
                                        ?: statusBarColor
                                        ?: appBarColor
                                        ?: historyIconColor
                                        ?: pipIconColor
                        newColor?.let { scope.launch { preferencesManager.addRecentColor(it) } }

                        // Save the colors
                        saveThemeSettingsWithCharacterCard {
                            when (currentColorPickerMode) {
                                "primary" ->
                                        primaryColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    customPrimaryColor = it
                                            )
                                        }
                                "secondary" ->
                                        secondaryColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    customSecondaryColor = it
                                            )
                                        }
                                "statusBar" ->
                                        statusBarColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    customStatusBarColor = it
                                            )
                                        }
                                "appBar" ->
                                        appBarColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    customAppBarColor = it
                                            )
                                        }
                                "historyIcon" ->
                                        historyIconColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    chatHeaderHistoryIconColor = it
                                            )
                                        }
                                "pipIcon" ->
                                        pipIconColor?.let {
                                            preferencesManager.saveThemeSettings(
                                                    chatHeaderPipIconColor = it
                                            )
                                        }
                            }
                        }
                    },
                    onDismiss = { showColorPicker = false }
            )
        }


    }
}

@Composable
private fun ThemeSectionTitle(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}
