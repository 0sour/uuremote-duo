package com.sour.duoremote.ui

/**
 * Top-level page ownership for the home stage. Login and key setup are separate stages.
 *
 * A live session is not a route: [Home] with a non-null video track renders the streaming
 * surface. [ControlCenter] temporarily covers the bottom panel while the top keeps the video.
 * [Files] is opened from the control center; its top pane becomes a read-only transfer summary.
 */
sealed interface AppRoute {
    data object Home : AppRoute
    data object Settings : AppRoute
    data object Account : AppRoute
    data object ControlCenter : AppRoute
    data object Files : AppRoute
}
