package com.sameerasw.essentials.island.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sameerasw.essentials.R
import java.math.BigDecimal

private val LocalInputReset = staticCompositionLocalOf { 0 }

/** The preview never scrolls away while editing parameters. Wide windows use two panes. */
@Composable
internal fun StudioScreen(
    onReset: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    var inputReset by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 700.dp
        val previewHeight = (maxHeight*0.32f).coerceIn(220.dp,300.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp,end = 12.dp,top = 4.dp,bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.media_preview_title),Modifier.weight(1f),style = MaterialTheme.typography.titleLarge)
                TextButton({ focusManager.clearFocus(); onReset(); inputReset++ }) {
                    Text(stringResource(R.string.media_preview_reset))
                }
            }
            CompositionLocalProvider(LocalInputReset provides inputReset) {
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    StudioControlPanel(Modifier.width(360.dp).fillMaxHeight(),controls)
                }
            } else {
                preview(Modifier.fillMaxWidth().height(previewHeight))
                HorizontalDivider(Modifier.padding(bottom = 12.dp),color = MaterialTheme.colorScheme.outlineVariant)
                StudioControlPanel(Modifier.weight(1f).fillMaxWidth(),controls)
            }
            }
        }
    }
}

@Composable
private fun StudioControlPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp,end = 16.dp,bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
}

@Composable
internal fun StudioPreview(
    modifier: Modifier,
    wallpaper: ImageBitmap,
    showWallpaper: Boolean,
    onWallpaperChange: (Boolean) -> Unit,
    fit: Boolean,
    onFitChange: (Boolean) -> Unit,
    cardWidth: Dp,
    cardHeight: Dp,
    effectOutset: Dp,
    card: @Composable (Modifier) -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF24262E))) {
            if (showWallpaper) Image(wallpaper,null,Modifier.fillMaxSize(),contentScale = ContentScale.Crop)
            val scale = if (fit) minOf(1f,(maxWidth-24.dp)/(cardWidth+effectOutset*2),(maxHeight-24.dp)/(cardHeight+effectOutset*2)) else 1f
            Box(Modifier.align(Alignment.Center)) {
                card(Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.media_preview_dimensions,cardWidth.value.toInt(),cardHeight.value.toInt()),
                Modifier.weight(1f),style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(showWallpaper,{ onWallpaperChange(!showWallpaper) },label = { Text(stringResource(R.string.media_preview_wallpaper)) })
            FilterChip(fit,{ onFitChange(true) },label = { Text(stringResource(R.string.media_preview_fit)) })
            FilterChip(!fit,{ onFitChange(false) },label = { Text("1:1") })
        }
    }
}

@Composable
internal fun StudioGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp),color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title,style = MaterialTheme.typography.titleMedium,color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
internal fun PreviewToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked,role = Role.Switch,onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium)
        Switch(checked,onCheckedChange = null)
    }
}

@Composable
internal fun StudioNumberInput(label: String,value: Float,unit: String,
    minimum: Float = 0f,maximum: Float? = null,exclusiveMinimum: Boolean = false,onChange: (Float) -> Unit) {
    fun format(number: Float) = BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reset = LocalInputReset.current
    var seenReset by remember { mutableIntStateOf(reset) }
    val focusManager = LocalFocusManager.current
    val invalidNumber = stringResource(R.string.media_preview_invalid_number)
    val lowerLimit = stringResource(if (exclusiveMinimum) R.string.media_preview_greater_than else R.string.media_preview_at_least,format(minimum))
    val upperLimit = maximum?.let { stringResource(R.string.media_preview_at_most,format(it)) }
    LaunchedEffect(value,reset) {
        if (reset != seenReset || !focused || !dirty) {
            text = format(value)
            dirty = false
            error = null
        }
        seenReset = reset
    }
    fun parse(input: String) = input.trim().replace(',','.').toFloatOrNull()
    fun validationError(number: Float?): String? = when {
        number == null || !number.isFinite() -> invalidNumber
        number < minimum || (exclusiveMinimum && number == minimum) -> lowerLimit
        maximum != null && number > maximum -> upperLimit
        else -> null
    }
    fun commit(): Boolean {
        val number = parse(text)
        error = validationError(number)
        if (error != null || number == null) return false
        onChange(number)
        text = format(number)
        dirty = false
        return true
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = text,onValueChange = {
            text = it
            dirty = true
            error = null
            // Preview valid drafts immediately, retaining the typed text/cursor and the last
            // valid preview while the user clears the field or types an incomplete decimal.
            val number = parse(it)
            if (number != null && validationError(number) == null) onChange(number)
        },
            modifier = Modifier.width(144.dp).onFocusChanged {
                val lostFocus = focused && !it.isFocused
                focused = it.isFocused
                if (lostFocus && dirty) commit()
            },singleLine = true,isError = error != null,suffix = { Text(unit) },
            supportingText = if (error != null) ({ Text(error.orEmpty()) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal,imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (commit()) focusManager.clearFocus() }))
    }
}
