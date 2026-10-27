package com.waivera.waivera.core.screenshottesting

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import org.robolectric.RuntimeEnvironment

/** Phone sizes every screen is checked on (spec: a small and a large phone). */
enum class ScreenshotDevice(
    val id: String,
    internal val widthDp: Int,
    internal val heightDp: Int,
    internal val density: String,
) {
    PhoneSmall(id = "small", widthDp = 360, heightDp = 640, density = "xhdpi"),
    PhoneLarge(id = "large", widthDp = 412, heightDp = 915, density = "xxhdpi"),
    ;

    internal fun qualifiers(darkTheme: Boolean): String {
        val night = if (darkTheme) "night" else "notnight"
        return "w${widthDp}dp-h${heightDp}dp-$night-$density"
    }
}

/** One cell of the matrix. */
data class ScreenshotVariant(val device: ScreenshotDevice, val darkTheme: Boolean, val fontScale: Float) {
    val suffix: String
        get() = buildString {
            append(device.id)
            append(if (darkTheme) "_dark" else "_light")
            append("_font")
            append((fontScale * PERCENT).toInt())
        }

    private companion object {
        const val PERCENT = 100
    }
}

/** Fixed matrix: light/dark × small/large phone × 100%/200% font = 8 images per call. */
object ScreenshotMatrix {
    val Default: List<ScreenshotVariant> = ScreenshotDevice.entries.flatMap { device ->
        listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> ScreenshotVariant(device = device, darkTheme = dark, fontScale = scale) }
        }
    }

    /** Where PNGs go. Roborazzi's Gradle plugin sets the property; the fallback matches its default. */
    val outputDirectory: String
        get() = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
}

/**
 * Renders [content] inside [WaiveraTheme] for every [variants] cell and writes
 * `<outputDirectory>/<name>_<device>_<theme>_font<percent>.png`.
 *
 * Call from a Robolectric test annotated `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and
 * `@Config(sdk = [ScreenshotSdk.VALUE])`. Motion is reduced so captures are deterministic and
 * haptics are off. Font scale is applied through [LocalDensity] so `sp` text scales exactly as
 * it does on device.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureScreenshots(
    name: String,
    variants: List<ScreenshotVariant> = ScreenshotMatrix.Default,
    content: @Composable () -> Unit,
) {
    // Animator scale 0 is the system "Remove animations" setting: Compose then stops infinite
    // animations (spinners, skeleton shimmer) instead of waiting on them before every capture.
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        0f,
    )
    variants.forEach { variant ->
        RuntimeEnvironment.setQualifiers(variant.device.qualifiers(variant.darkTheme))
        captureRoboImage(filePath = "${ScreenshotMatrix.outputDirectory}/${name}_${variant.suffix}.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                WaiveraTheme(
                    darkTheme = variant.darkTheme,
                    motion = WaiveraMotionScheme.Reduced,
                    hapticsEnabled = false,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(WaiveraTheme.colors.background),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** The Robolectric SDK screenshots render on. Pinned so images do not change with the compile SDK. */
object ScreenshotSdk {
    const val VALUE = 35
}
