package com.javzzap.laxbrowser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R

/**
 * Home screen search bar: tapping it opens a new tab with the address bar focused, and the
 * mask button opens a new private tab instead. It shows no browsing data, so it never updates.
 */
class SearchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search).apply {
            val search = open(context, MainActivity.ACTION_WIDGET_SEARCH, REQUEST_SEARCH)
            setOnClickPendingIntent(android.R.id.background, search)
            setOnClickPendingIntent(R.id.widget_search, search)
            setOnClickPendingIntent(R.id.widget_private, open(context, MainActivity.ACTION_WIDGET_PRIVATE, REQUEST_PRIVATE))
        }
        widgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun open(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_SEARCH = 1
        const val REQUEST_PRIVATE = 2
    }
}
