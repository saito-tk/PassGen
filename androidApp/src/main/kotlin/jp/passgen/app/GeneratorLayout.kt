package jp.passgen.app

internal fun generatorOrientation(smallestWidthDp: Int): Int = if (smallestWidthDp < 600)
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

/** Window-local dp, not screen pixels. A physical separating hinge takes precedence over proportions. */
internal data class GeneratorSplit(val settingsWidth: Float, val gap: Float)
internal data class GeneratorRegion(val start: Float, val length: Float)
/** Keep controls wholly on the larger side when an asymmetric hinge cannot fit both panes. */
internal fun generatorRegion(total: Float, start: Float?, end: Float?, minimumSide: Float): GeneratorRegion {
    if (start == null || end == null || !start.isFinite() || !end.isFinite() || end < start || end <= 0 || start >= total)
        return GeneratorRegion(0f, total)
    val left = start.coerceIn(0f, total); val right = end.coerceIn(0f, total)
    if (left >= minimumSide && total - right >= minimumSide) return GeneratorRegion(0f, total)
    return if (left >= total - right) GeneratorRegion(0f, left) else GeneratorRegion(right, total - right)
}
internal fun generatorTabletop(height: Float, top: Float?, bottom: Float?): GeneratorSplit? {
    if (top == null || bottom == null || !top.isFinite() || !bottom.isFinite() || top < 180 || bottom < top || height - bottom < 180) return null
    return GeneratorSplit(top, bottom - top)
}
internal fun generatorSplit(width: Float, hingeStart: Float? = null, hingeEnd: Float? = null): GeneratorSplit? {
    if (!width.isFinite() || width <= 0) return null
    if (hingeStart != null && hingeEnd != null && hingeStart.isFinite() && hingeEnd.isFinite() &&
        hingeStart >= 344 && hingeEnd >= hingeStart && width - hingeEnd >= 344) {
        return GeneratorSplit(hingeStart, hingeEnd - hingeStart)
    }
    // Never put a column across a separating hinge that cannot fit both panes.
    if (hingeStart != null && hingeEnd != null && hingeStart in 0f..width && hingeEnd in hingeStart..width) return null
    if (width < 720) return null
    // Both panes get the same usable width; an arbitrary ratio/cap made the
    // unfolded editor narrower than the cover display and wasted space on results.
    return GeneratorSplit(width / 2f, 0f)
}
