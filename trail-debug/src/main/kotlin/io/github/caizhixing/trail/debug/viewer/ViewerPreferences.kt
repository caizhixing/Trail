package io.github.caizhixing.trail.debug.viewer

import android.content.Context

/**
 * Concise/full view memory seam (behavior 5.1, P-1). `null` from
 * [conciseView] means "never explicitly chosen" — the caller falls back to
 * the first-launch default.
 */
internal interface ViewerPreferences {

    fun conciseView(): Boolean?

    fun setConciseView(value: Boolean)
}

/**
 * SharedPreferences-backed implementation (P-1: lightweight debug storage,
 * no DataStore dependency).
 */
internal class SharedPreferencesViewerPreferences(
    context: Context,
) : ViewerPreferences {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun conciseView(): Boolean? = if (prefs.contains(KEY_CONCISE_VIEW)) {
        prefs.getBoolean(KEY_CONCISE_VIEW, ConciseViewPolicy.DEFAULT_CONCISE)
    } else {
        null
    }

    override fun setConciseView(value: Boolean) {
        prefs.edit().putBoolean(KEY_CONCISE_VIEW, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "trail_viewer_prefs"
        const val KEY_CONCISE_VIEW = "concise_view"
    }
}
