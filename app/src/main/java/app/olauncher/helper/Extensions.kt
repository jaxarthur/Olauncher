package app.olauncher.helper

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun View.hideKeyboard() {
    this.clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard(show: Boolean = true) {
    if (show.not()) return
    if (this.requestFocus()) {
        postDelayed({
            val activity =
                this.rootView.findViewById<View>(android.R.id.content).context as Activity
            val window = activity.window
            WindowCompat.getInsetsController(window, this).show(WindowInsetsCompat.Type.ime())
        }, 100)
    }

}