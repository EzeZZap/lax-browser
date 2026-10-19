package com.javzzap.laxbrowser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R

/**
 * Home screen search bar: tapping it opens a new tab with the address bar focused, and the
 * mask button opens a new private tab instead. Each widget can be set to one Space (and so its
 * container); without one, the tab opens in the Space that is open in Lax.
 */
class SearchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { manager.updateAppWidget(it, views(context, it)) }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        widgetIds.forEach { SearchWidgetSettings.clear(context, it) }
    }

    companion object {
        /** Redraws one widget after its Space changed. */
        fun refresh(context: Context, widgetId: Int) {
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views(context, widgetId))
        }

        private fun views(context: Context, widgetId: Int): RemoteViews {
            val space = SearchWidgetSettings.space(context, widgetId)
            return RemoteViews(context.packageName, R.layout.widget_search).apply {
                setTextViewText(
                    R.id.widget_search,
                    space?.let { context.getString(R.string.widget_search_hint_space, it.name) } ?: context.getString(R.string.widget_search_hint),
                )
                val search = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WIDGET_SEARCH)
                space?.let { search.putExtra(MainActivity.EXTRA_SPACE_ID, it.id) }
                val searchIntent = pending(context, search, widgetId * 2)
                setOnClickPendingIntent(android.R.id.background, searchIntent)
                setOnClickPendingIntent(R.id.widget_search, searchIntent)
                val private = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WIDGET_PRIVATE)
                setOnClickPendingIntent(R.id.widget_private, pending(context, private, widgetId * 2 + 1))
            }
        }

        // Each widget needs its own request codes: PendingIntents that differ only in extras are the same.
        private fun pending(context: Context, intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** The Space each search widget opens tabs in, by widget ID. The name is kept for the widget's hint. */
object SearchWidgetSettings {
    data class WidgetSpace(val id: String, val name: String)

    private const val PREFS = "search_widgets"

    fun space(context: Context, widgetId: Int): WidgetSpace? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("space_$widgetId", null) ?: return null
        return WidgetSpace(id, prefs.getString("name_$widgetId", null).orEmpty())
    }

    /** A null [space] means the Space that is open in Lax. */
    fun setSpace(context: Context, widgetId: Int, space: WidgetSpace?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (space == null) {
                remove("space_$widgetId")
                remove("name_$widgetId")
            } else {
                putString("space_$widgetId", space.id)
                putString("name_$widgetId", space.name)
            }
        }
    }

    fun clear(context: Context, widgetId: Int) = setSpace(context, widgetId, null)
}
