package com.waivera.waivera.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light, dark and 200% font scale, the three previews every reusable composable needs. */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "200% font", showBackground = true, fontScale = 2f)
annotation class WaiveraPreviews
