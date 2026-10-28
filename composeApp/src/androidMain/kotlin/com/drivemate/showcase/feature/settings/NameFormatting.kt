package com.drivemate.showcase.feature.settings

/**
 * Display-only title case. Only touches a word that is entirely lowercase --
 * a word with any uppercase letter (already capitalized, an acronym, or an
 * intentional mixed case) is left untouched, so this never mangles a name
 * that's already correctly cased in storage. Never writes back to storage.
 */
fun String.toDisplayTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        if (word.isNotEmpty() && word.all { !it.isLetter() || it.isLowerCase() }) {
            word.replaceFirstChar { it.uppercase() }
        } else {
            word
        }
    }
