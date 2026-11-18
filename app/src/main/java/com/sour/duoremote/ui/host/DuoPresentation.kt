package com.sour.duoremote.ui.host

import android.app.Presentation
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.sour.duoremote.ui.theme.DuoRemoteTheme

/**
 * Compose host for the Duo bottom display.
 *
 * Presentation is a Dialog subclass, so it gets its own window but no Lifecycle/ViewModelStore.
 * Compose needs all three owners; this class supplies them and keeps the registry in sync.
 * Do not put Dialog/AlertDialog/ModalBottomSheet in this window, use Compose overlays instead.
 */
class DuoPresentation(
    context: Context,
    display: Display,
    private val onBack: () -> Unit,
    private val onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    private val onGenericMotionEvent: ((MotionEvent) -> Boolean)? = null,
    private val normalizeKey: ((KeyEvent) -> KeyEvent)? = null,
    private val content: @Composable () -> Unit,
) : Presentation(context, display),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val backDispatcher = OnBackPressedDispatcher(Runnable { onBack() })

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = backDispatcher

    init {
        savedStateController.performRestore(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        val composeView = ComposeView(context).apply {
            ViewTreeOwners.install(
                this,
                this@DuoPresentation,
                this@DuoPresentation,
                this@DuoPresentation,
                this@DuoPresentation,
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                DuoRemoteTheme {
                    content()
                }
            }
        }
        setContentView(composeView)
        // Presentation windows do not hand input focus to the ComposeView automatically.
        composeView.post { composeView.requestFocus() }
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun dismiss() {
        if (isShowing) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
        super.dismiss()
    }

    override fun dispatchKeyEvent(raw: KeyEvent): Boolean {
        // Normalize Retro/Xbox face buttons once; Compose handlers below rely on canonical keys.
        val event = normalizeKey?.invoke(raw) ?: raw
        if (onKeyEvent?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (onGenericMotionEvent?.invoke(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    @Deprecated("Dialog back routing")
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
    }
}
