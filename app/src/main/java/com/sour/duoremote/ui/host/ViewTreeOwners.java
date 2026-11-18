package com.sour.duoremote.ui.host;

import android.view.View;

import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.ViewTreeOnBackPressedDispatcherOwner;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/**
 * Small Java bridge for the ViewTree owner helpers.
 *
 * Keeping this in Java avoids Kotlin metadata resolution quirks for these
 * androidx classes in some toolchain combinations.
 */
public final class ViewTreeOwners {

    private ViewTreeOwners() {
    }

    public static void install(
            View view,
            LifecycleOwner lifecycleOwner,
            ViewModelStoreOwner viewModelStoreOwner,
            SavedStateRegistryOwner savedStateRegistryOwner,
            OnBackPressedDispatcherOwner backPressedDispatcherOwner
    ) {
        ViewTreeLifecycleOwner.set(view, lifecycleOwner);
        ViewTreeViewModelStoreOwner.set(view, viewModelStoreOwner);
        ViewTreeSavedStateRegistryOwner.set(view, savedStateRegistryOwner);
        ViewTreeOnBackPressedDispatcherOwner.set(view, backPressedDispatcherOwner);
    }
}
