@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Exact Google Material Symbols; local vectors keep icons available offline. */
@Composable internal fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(24.dp))
}

@Composable internal fun IconAction(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, badge: Boolean = false, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, enabled = enabled, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
                if (badge) Surface(Modifier.align(Alignment.TopEnd).size(14.dp), shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest) { AppIcon(R.drawable.ic_check_circle, Modifier.size(14.dp)) }
            }
        }
    }
}

@Composable internal fun ActionLabel(@DrawableRes icon: Int, label: String) {
    AppIcon(icon, Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

@Composable internal fun SheetHeading(title: String, @DrawableRes icon: Int, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(icon)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A modal uses one unobstructed side, including when the main screen spans a separating hinge. */
@Composable internal fun PassgenSheet(dismiss: () -> Unit, fold: androidx.window.layout.FoldingFeature?,
    modifier: Modifier = Modifier, maxWidth: androidx.compose.ui.unit.Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val size = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val width = with(density) { size.width.toDp().value }
    val height = with(density) { size.height.toDp().value }
    val vertical = fold?.orientation == androidx.window.layout.FoldingFeature.Orientation.VERTICAL
    val horizontal = fold?.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL
    val x = if (vertical) generatorRegion(width, with(density) { fold!!.bounds.left.toDp().value }, with(density) { fold!!.bounds.right.toDp().value }, Float.POSITIVE_INFINITY) else GeneratorRegion(0f, width)
    val y = if (horizontal) generatorRegion(height, with(density) { fold!!.bounds.top.toDp().value }, with(density) { fold!!.bounds.bottom.toDp().value }, Float.POSITIVE_INFINITY) else GeneratorRegion(0f, height)
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = if (x.length < width) androidx.compose.ui.unit.Dp.Unspecified else maxWidth,
        modifier = modifier.padding(start = x.start.dp, end = (width - x.start - x.length).coerceAtLeast(0f).dp,
            top = y.start.dp, bottom = (height - y.start - y.length).coerceAtLeast(0f).dp), content = content)
}
