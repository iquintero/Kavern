package com.iquintero.kavern.ui.reader.screen

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iquintero.kavern.R
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.ImageReaderMode
import com.iquintero.kavern.core.storage.ReaderPrefs
import com.iquintero.kavern.core.storage.ReaderThemeMode
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.ui.reader.model.ReaderFontOption
import com.iquintero.kavern.ui.reader.model.fontFaceCss
import com.iquintero.kavern.ui.reader.model.readerFontOptions
import com.iquintero.kavern.ui.reader.model.readerThemeOptions
import com.iquintero.kavern.ui.reader.renderer.BaseReader
import com.iquintero.kavern.ui.reader.renderer.PdfReader
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderCallbacks
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderParams
import com.iquintero.kavern.ui.reader.viewmodel.BaseReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ImageReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.PdfReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ReaderUiState
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max

data class ReaderSettingsState(
    val selectedTab: ReaderSettingsTab,
    val fontSize: Float,
    val lineHeight: Float,
    val padding: Dp,
    val paddingVertical: Dp,
    val themeMode: ReaderThemeMode,
    val textAlign: TextAlign,
    val fontOptions: List<ReaderFontOption>,
    val fontFamilyId: String,
    val imageReaderMode: ImageReaderMode,
    val epubPaged: Boolean = false,
)

data class ReaderSettingsCallbacks(
    val onSelectTab: (ReaderSettingsTab) -> Unit,
    val onFontSizeChange: (Float) -> Unit,
    val onLineHeightChange: (Float) -> Unit,
    val onPaddingChange: (Dp) -> Unit,
    val onPaddingVerticalChange: (Dp) -> Unit,
    val onThemeChange: (ReaderThemeMode) -> Unit,
    val onTextAlignChange: (TextAlign) -> Unit,
    val onFontFamilyChange: (ReaderFontOption) -> Unit,
    val onImageReaderModeChange: (ImageReaderMode) -> Unit,
    val onEpubPagedChange: (Boolean) -> Unit = {},
)

@Suppress("LongParameterList")
@Composable
internal fun SettingsPanel(
    selectedTab: ReaderSettingsTab,
    onSelectTab: (ReaderSettingsTab) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    padding: Dp,
    onPaddingChange: (Dp) -> Unit,
    paddingVertical: Dp,
    onPaddingVerticalChange: (Dp) -> Unit,
    themeMode: ReaderThemeMode,
    onThemeChange: (ReaderThemeMode) -> Unit,
    textAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    fontOptions: List<ReaderFontOption>,
    fontFamilyId: String,
    onFontFamilyChange: (ReaderFontOption) -> Unit,
    epubPaged: Boolean,
    onEpubPagedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    ReaderSettingsSheet(
        onClose = onClose,
        tabs = { ReaderSettingsTabs(selectedTab, onSelectTab) },
    ) {
        when (selectedTab) {
            ReaderSettingsTab.Font ->
                FontSettings(
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    lineHeight = lineHeight,
                    onLineHeightChange = onLineHeightChange,
                    padding = padding,
                    onPaddingChange = onPaddingChange,
                    paddingVertical = paddingVertical,
                    onPaddingVerticalChange = onPaddingVerticalChange,
                    textAlign = textAlign,
                    onTextAlignChange = onTextAlignChange,
                    fontOptions = fontOptions,
                    fontFamilyId = fontFamilyId,
                    onFontFamilyChange = onFontFamilyChange,
                )
            ReaderSettingsTab.Theme ->
                ThemeSettings(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                )
            ReaderSettingsTab.Navigation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reader_paged_mode), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.reader_paged_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = epubPaged, onCheckedChange = onEpubPagedChange)
                }
        }
    }
}

/**
 * The frame every reader settings panel shares: a card with a title and an explicit close button, optional pinned
 * tabs, and content that scrolls when it is taller than half the screen so the book stays visible above it.
 */
@Composable
internal fun ReaderSettingsSheet(
    onClose: () -> Unit,
    tabs: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val eink = LocalEinkMode.current
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * SHEET_CONTENT_SCREEN_FRACTION).dp
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (eink) 0.dp else 8.dp,
        border = einkBorder() ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_reader_settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.general_close))
                }
            }
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tabs?.invoke()
                Column(
                    modifier = Modifier.heightIn(max = maxContentHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

private const val SHEET_CONTENT_SCREEN_FRACTION = 0.5f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderSettingsTabs(
    selected: ReaderSettingsTab,
    onSelect: (ReaderSettingsTab) -> Unit,
) {
    val tabs =
        listOf(
            Triple(ReaderSettingsTab.Font, Icons.Filled.FormatSize, R.string.reader_tab_text),
            Triple(ReaderSettingsTab.Theme, Icons.Filled.Palette, R.string.reader_tab_theme),
            Triple(ReaderSettingsTab.Navigation, Icons.Filled.SwapHoriz, R.string.reader_tab_paging),
        )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (tab, icon, label) ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

public enum class ReaderSettingsTab(
    val label: String,
) {
    Font("Font"),
    Theme("Theme"),
    Navigation("Navigation"),
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun FontSettings(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    padding: Dp,
    onPaddingChange: (Dp) -> Unit,
    paddingVertical: Dp,
    onPaddingVerticalChange: (Dp) -> Unit,
    textAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    fontOptions: List<ReaderFontOption>,
    fontFamilyId: String,
    onFontFamilyChange: (ReaderFontOption) -> Unit,
) {
    var fontMenuExpanded by remember { mutableStateOf(false) }
    val selectedFontLabel = fontOptions.firstOrNull { it.id == fontFamilyId }?.label ?: stringResource(R.string.reader_font_selection)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperRow(stringResource(R.string.reader_font_size), valueText = "${fontSize.toInt()}") {
            onFontSizeChange((fontSize + it))
        }
        StepperRow(stringResource(R.string.reader_line_height), valueText = String.format("%.1f", lineHeight)) {
            onLineHeightChange((lineHeight + it * 0.1f))
        }
        StepperRow(stringResource(R.string.reader_padding), valueText = "${padding.value.toInt()} dp") {
            onPaddingChange((padding + it.dp))
        }
        StepperRow(stringResource(R.string.reader_padding_vertical), valueText = "${paddingVertical.value.toInt()} dp") {
            onPaddingVerticalChange((paddingVertical + (it * ReaderPrefs.VERTICAL_PADDING_STEP_DP).dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.reader_alignment), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val alignItems =
                listOf(
                    Triple(TextAlign.Start, Icons.Filled.FormatAlignLeft, R.string.reader_align_start),
                    Triple(TextAlign.Center, Icons.Filled.FormatAlignCenter, R.string.reader_align_center),
                    Triple(TextAlign.End, Icons.Filled.FormatAlignRight, R.string.reader_align_end),
                    Triple(TextAlign.Justify, Icons.Filled.FormatAlignJustify, R.string.reader_align_justify),
                )
            alignItems.forEach { (align, icon, label) ->
                IconToggleButton(
                    checked = align == textAlign,
                    onCheckedChange = { onTextAlignChange(align) },
                    colors =
                        IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(icon, contentDescription = stringResource(label))
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = fontMenuExpanded,
            onExpandedChange = { fontMenuExpanded = it },
        ) {
            TextField(
                value = selectedFontLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.reader_font_family)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontMenuExpanded) },
                colors =
                    ExposedDropdownMenuDefaults.textFieldColors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = fontMenuExpanded,
                onDismissRequest = { fontMenuExpanded = false },
                modifier = Modifier.exposedDropdownSize(),
            ) {
                fontOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            fontMenuExpanded = false
                            onFontFamilyChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettings(
    themeMode: ReaderThemeMode,
    onThemeChange: (ReaderThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            readerThemeOptions.forEach { option ->
                FilterChip(
                    selected = themeMode == option.mode,
                    onClick = { onThemeChange(option.mode) },
                    label = { Text(stringResource(option.labelRes)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun ReaderSettingsPanel(
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    ReaderSettingsSheet(onClose = onClose, content = content)
}

@Composable
internal fun PdfReadingDirectionSettings(
    rightToLeft: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.reader_pdf_reading_direction), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !rightToLeft,
            onClick = { onChange(false) },
            label = { Text(stringResource(R.string.reader_image_mode_ltr)) },
        )
        FilterChip(
            selected = rightToLeft,
            onClick = { onChange(true) },
            label = { Text(stringResource(R.string.reader_image_mode_rtl)) },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ImageReadingModeSettings(
    mode: ImageReaderMode,
    onModeChange: (ImageReaderMode) -> Unit,
) {
    val options =
        listOf(
            ImageReaderMode.LeftToRight to R.string.reader_image_mode_ltr,
            ImageReaderMode.RightToLeft to R.string.reader_image_mode_rtl,
            ImageReaderMode.Vertical to R.string.reader_image_mode_vertical,
            ImageReaderMode.Webtoon to R.string.reader_image_mode_webtoon,
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reader_image_default_mode), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.reader_image_default_mode_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (option, labelRes) ->
                FilterChip(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    label = { Text(stringResource(labelRes)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onDelta: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = { onDelta(-1f) }) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.general_decrease))
        }
        // A fixed width keeps the buttons in the same place whatever the value reads.
        Text(
            valueText,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(STEPPER_VALUE_WIDTH),
        )
        OutlinedIconButton(onClick = { onDelta(1f) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.general_increase))
        }
    }
}

private val STEPPER_VALUE_WIDTH = 64.dp
