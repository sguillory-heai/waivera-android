package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced

/** True when the cache holds something to show, whether or not the server has confirmed it yet. */
internal fun Synced<*>.hasContent(): Boolean = when (val value = data) {
    is Collection<*> -> value.isNotEmpty()
    is ActivityFeed -> value.items.isNotEmpty()
    is ScheduleFeed -> value.events.isNotEmpty() || value.timeOff.isNotEmpty() || value.holidays.isNotEmpty()
    else -> value != null
}

internal fun Synced<*>.isLoading(): Boolean = lastSyncedAt == null && error == null && !hasContent()
