package com.drivemate.showcase.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle

/**
 * Reads and removes a one-shot [Long] result from [handle] exactly once per
 * fresh entry into composition.
 *
 * Deliberately plain `remember`, NOT `rememberSaveable`: Navigation Compose
 * persists `rememberSaveable` state per back-stack entry across navigate-away
 * -and-back (the same mechanism that restores scroll position etc.), so a
 * `rememberSaveable` here would keep restoring the already-consumed value
 * every time the destination is revisited -- e.g. opening the checklist from
 * the snackbar's action and pressing back would show the snackbar again.
 * Plain `remember` has no such persistence: a fresh composition (including
 * one restored after a rotation) re-runs `remove()`, which by then returns
 * null because the value was already taken the first time.
 */
@Composable
fun rememberConsumedLongResult(handle: SavedStateHandle, key: String): Long? =
    remember(handle) { handle.remove<Long>(key) }
