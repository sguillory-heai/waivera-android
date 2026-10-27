package com.waivera.waivera.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.waivera.waivera.core.designsystem.R

/**
 * Icon set as vector drawables, so the app does not depend on the frozen Material icons
 * artifact. Draw them with `Icon(painterResource(WaiveraIcons.Back), contentDescription)`.
 */
object WaiveraIcons {
    @DrawableRes val Add: Int = R.drawable.core_designsystem_ic_add

    @DrawableRes val Back: Int = R.drawable.core_designsystem_ic_back

    @DrawableRes val Bookmark: Int = R.drawable.core_designsystem_ic_bookmark

    @DrawableRes val Calendar: Int = R.drawable.core_designsystem_ic_calendar

    @DrawableRes val Chat: Int = R.drawable.core_designsystem_ic_chat

    @DrawableRes val Check: Int = R.drawable.core_designsystem_ic_check

    @DrawableRes val ChevronRight: Int = R.drawable.core_designsystem_ic_chevron_right

    @DrawableRes val Close: Int = R.drawable.core_designsystem_ic_close

    @DrawableRes val CloudOff: Int = R.drawable.core_designsystem_ic_cloud_off

    @DrawableRes val Error: Int = R.drawable.core_designsystem_ic_error

    @DrawableRes val Info: Int = R.drawable.core_designsystem_ic_info

    @DrawableRes val Notifications: Int = R.drawable.core_designsystem_ic_notifications

    @DrawableRes val People: Int = R.drawable.core_designsystem_ic_people

    @DrawableRes val Person: Int = R.drawable.core_designsystem_ic_person

    @DrawableRes val Refresh: Int = R.drawable.core_designsystem_ic_refresh

    @DrawableRes val Schedule: Int = R.drawable.core_designsystem_ic_schedule

    @DrawableRes val Search: Int = R.drawable.core_designsystem_ic_search

    @DrawableRes val Settings: Int = R.drawable.core_designsystem_ic_settings

    @DrawableRes val Today: Int = R.drawable.core_designsystem_ic_today

    @DrawableRes val Warning: Int = R.drawable.core_designsystem_ic_warning
}
