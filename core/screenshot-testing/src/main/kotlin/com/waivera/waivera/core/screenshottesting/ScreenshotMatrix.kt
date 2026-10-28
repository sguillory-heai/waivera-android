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
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.waivera.waivera.core.data.image.AuthenticatedImageLoader
import com.waivera.waivera.core.demo.DemoBackend
import com.waivera.waivera.core.demo.DemoPeopleRepository
import com.waivera.waivera.core.designsystem.component.LocalWaiveraSkeletonDelay
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
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

    internal fun qualifiers(darkTheme: Boolean, heightOverrideDp: Int? = null): String {
        val night = if (darkTheme) "night" else "notnight"
        return "w${widthDp}dp-h${heightOverrideDp ?: heightDp}dp-$night-$density"
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
 * [heightDp] gives a taller window than the phone, for states that sit below the first screen
 * (a section near the end of a list): the phone width stays the same, so layout is unchanged and
 * the state under test is in the image instead of cropped off.
 *
 * Call from a Robolectric test annotated `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and
 * `@Config(sdk = [ScreenshotSdk.VALUE])`. Motion is reduced so captures are deterministic and
 * haptics are off. Font scale is applied through [LocalDensity] so `sp` text scales exactly as
 * it does on device.
 *
 * [animations] keeps the system animator scale at 1 and the theme on standard motion, for a
 * capture of a seeked transition such as the predictive back preview halfway through a swipe;
 * with animations off a seek snaps straight to its end (UI4-10). Only use it for content with no
 * infinite animation, which would never let the capture settle.
 *
 * People's photos load through the app's image loader over the demo people, on the main thread,
 * so avatars show photos as they do in the app instead of initials (UI4-10).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureScreenshots(
    name: String,
    variants: List<ScreenshotVariant> = ScreenshotMatrix.Default,
    heightDp: Int? = null,
    animations: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Animator scale 0 is the system "Remove animations" setting: Compose then stops infinite
    // animations (spinners, skeleton shimmer) instead of waiting on them before every capture.
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        if (animations) 1f else 0f,
    )
    installDemoImageLoader()
    val motion = if (animations) WaiveraMotionScheme.Standard else WaiveraMotionScheme.Reduced
    variants.forEach { variant ->
        RuntimeEnvironment.setQualifiers(variant.device.qualifiers(variant.darkTheme, heightDp))
        captureRoboImage(filePath = "${ScreenshotMatrix.outputDirectory}/${name}_${variant.suffix}.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, variant.fontScale),
                LocalWaiveraSkeletonDelay provides 0L,
            ) {
                WaiveraTheme(
                    darkTheme = variant.darkTheme,
                    motion = motion,
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

/**
 * The app's [AuthenticatedImageLoader] over the demo people (Priya has a photo), decoding on the
 * main thread so the photo is in the frame Roborazzi captures, as the navigation tour does.
 */
@OptIn(DelicateCoilApi::class)
private fun installDemoImageLoader() {
    val people = DemoPeopleRepository(DemoBackend())
    val loader = AuthenticatedImageLoader(
        context = RuntimeEnvironment.getApplication(),
        client = OkHttpClient(),
        people = { people },
    ).imageLoader.newBuilder()
        .coroutineContext(Dispatchers.Main.immediate)
        .build()
    SingletonImageLoader.setUnsafe(loader)
}

/** Window heights for [captureScreenshots] when the state under test is below the first screen. */
object ScreenshotHeights {
    /** Fits a full Today or Profile screen at 200% font on the small phone. */
    const val TALL_DP = 2400
}

/** The Robolectric SDK screenshots render on. Pinned so images do not change with the compile SDK. */
object ScreenshotSdk {
    const val VALUE = 35
}
