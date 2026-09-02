package com.notcan.app.ui.home

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * WebView de apuntes que conserva la selección nativa de Android y añade la
 * acción Subrayar al floating ActionMode. Es el mismo enfoque usado por
 * Ministerium: tiradores y copiar/compartir siguen siendo responsabilidad del
 * sistema; NotCan solo agrega su acción académica.
 */
internal class NativeSelectionWebView(context: Context) : WebView(context) {
    private interface WrappedSelectionCallback

    private var onHighlightRequested: (() -> Unit)? = null
    private var lastSelectionX = -1f
    private var lastSelectionY = -1f

    fun setOnHighlightRequested(callback: (() -> Unit)?) {
        onHighlightRequested = callback
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_MOVE -> {
                    lastSelectionX = event.x
                    lastSelectionY = event.y
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(wrap(callback), ActionMode.TYPE_FLOATING)
        } else {
            super.startActionMode(wrap(callback))
        }
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(wrap(callback), ActionMode.TYPE_FLOATING)
        } else {
            super.startActionMode(wrap(callback), type)
        }
    }

    private fun wrap(original: ActionMode.Callback): ActionMode.Callback {
        if (original is WrappedSelectionCallback) return original
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SelectionCallback2(original)
        } else {
            SelectionCallback(original)
        }
    }

    private fun populate(menu: Menu?) {
        if (menu == null || menu.findItem(ACTION_HIGHLIGHT) != null) return
        menu.add(Menu.NONE, ACTION_HIGHLIGHT, 0, "Subrayar").apply {
            setIcon(android.R.drawable.ic_menu_edit)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun onCreate(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        // Insertar antes de delegar hace que Android coloque Subrayar al inicio del
        // floating toolbar; luego volvemos a asegurarla por si Chromium recreó el menú.
        populate(menu)
        val created = original.onCreateActionMode(mode, menu)
        if (created) populate(menu)
        return created
    }

    private fun onPrepare(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        val changed = original.onPrepareActionMode(mode, menu)
        populate(menu)
        return changed || menu.findItem(ACTION_HIGHLIGHT) != null
    }

    private fun onClicked(original: ActionMode.Callback, mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == ACTION_HIGHLIGHT) {
            // La selección queda clonada dentro del documento JS; se puede cerrar el
            // ActionMode sin perder el rango que después estiliza el diálogo.
            onHighlightRequested?.invoke()
            mode.finish()
            return true
        }
        return original.onActionItemClicked(mode, item)
    }

    private fun fallbackRect(view: View, outRect: Rect) {
        val density = resources.displayMetrics.density
        val halfWidth = maxOf(20, (28f * density).toInt())
        val halfHeight = maxOf(14, (20f * density).toInt())
        val x = if (lastSelectionX >= 0f) lastSelectionX.toInt() else view.width / 2
        val y = if (lastSelectionY >= 0f) lastSelectionY.toInt() else view.height / 2
        val left = maxOf(0, x - halfWidth)
        val top = maxOf(0, y - halfHeight)
        val right = minOf(view.width, x + halfWidth).coerceAtLeast(left + 1)
        val bottom = minOf(view.height, y + halfHeight).coerceAtLeast(top + 1)
        outRect.set(left, top, right, bottom)
    }

    private fun unusableRect(view: View, rect: Rect): Boolean {
        if (rect.isEmpty) return true
        val viewArea = maxOf(1, view.width).toLong() * maxOf(1, view.height).toLong()
        val rectArea = maxOf(0, rect.width()).toLong() * maxOf(0, rect.height()).toLong()
        if (rectArea * 100L > viewArea * 55L) return true
        return rect.right < 0 || rect.bottom < 0 || rect.left > view.width || rect.top > view.height
    }

    private inner class SelectionCallback(
        private val original: ActionMode.Callback
    ) : ActionMode.Callback, WrappedSelectionCallback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(original, mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(original, mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = onClicked(original, mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)
    }

    private inner class SelectionCallback2(
        private val original: ActionMode.Callback
    ) : ActionMode.Callback2(), WrappedSelectionCallback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(original, mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(original, mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = onClicked(original, mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (original is ActionMode.Callback2) original.onGetContentRect(mode, view, outRect)
            else outRect.setEmpty()
            if (unusableRect(view, outRect)) fallbackRect(view, outRect)
        }
    }

    companion object {
        private const val ACTION_HIGHLIGHT = 9301
    }
}
