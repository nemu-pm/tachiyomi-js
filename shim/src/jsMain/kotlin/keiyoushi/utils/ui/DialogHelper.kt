package keiyoushi.utils.ui

// Stub for keiyoushi core utils/ui/DialogHelper.kt. The sandbox has no activity to show a
// dialog on, so every prompt resolves the way a dismissed dialog would: info returns, confirm
// is false, inputs and selections are null. Signatures match upstream so sources compile.

import android.util.Log

private const val TAG = "DialogHelper"

/**
 * Shows a message with a single button and suspends until it is closed.
 * Back press or tapping outside also counts as closing, and does not throw.
 */
@Suppress("UNUSED_PARAMETER")
suspend fun showInfo(
    title: String,
    message: String,
    buttonText: String = "OK",
) {
    Log.i(TAG, "$title: $message")
}

/**
 * Shows a yes/no question.
 *
 * @return `true` for the positive button, `false` for the negative button.
 * Back press or tapping outside also returns `false`.
 */
@Suppress("UNUSED_PARAMETER")
suspend fun askConfirm(
    title: String,
    message: String,
    yesText: String = "Yes",
    noText: String = "No",
): Boolean {
    Log.i(TAG, "$title: $message (no dialog in the sandbox, answering no)")
    return false
}

/**
 * Shows a dialog with a single text field.
 *
 * @return The text the user entered, or `null` if they canceled or dismissed the dialog.
 */
@Suppress("UNUSED_PARAMETER")
suspend fun askInput(
    title: String,
    message: String? = null,
    hint: String? = null,
    initialValue: String = "",
    password: Boolean = false,
    required: Boolean = true,
): String? {
    Log.i(TAG, "$title (no dialog in the sandbox, input dismissed)")
    return null
}

/** Shorthand for [askInput] with a masked field. See [askInput] for the parameters. */
suspend fun askPassword(
    title: String = "Password Required",
    message: String? = null,
    hint: String? = "Password",
    required: Boolean = true,
): String? = askInput(
    title = title,
    message = message,
    hint = hint,
    password = true,
    required = required,
)

/**
 * Shows a single-choice list of [options].
 *
 * @return The 0-based index of the chosen option, or `null` if canceled.
 */
@Suppress("UNUSED_PARAMETER")
suspend fun askSelect(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): Int? {
    if (options.isEmpty()) return null
    Log.i(TAG, "$title (no dialog in the sandbox, selection dismissed)")
    return null
}

/** Shorthand for [askSelect] that returns the selected option string directly. */
suspend fun askSelectOption(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): String? = askSelect(title, options, selectedIndex, message)?.let { options.getOrNull(it) }
