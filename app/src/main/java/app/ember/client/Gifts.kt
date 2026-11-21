package app.ember.client

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.zip.GZIPInputStream

/** Draws the sticker of a Telegram gift (animated .tgs or static .webp). */
@Composable
fun GiftSticker(gift: TdApi.Gift, size: Dp, animate: Boolean = true) {
    val sticker = gift.sticker
    var path by remember(sticker.sticker.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(sticker.sticker.id) {
        path = try { Td.downloadPath(sticker.sticker) } catch (_: Exception) { null }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val p = path
        when {
            p != null && sticker.format is TdApi.StickerFormatTgs -> TgsView(p, size, animate)
            p != null && sticker.format is TdApi.StickerFormatWebp -> WebpView(p, size)
            else -> Text(sticker.emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
private fun TgsView(path: String, size: Dp, animate: Boolean) {
    val json = remember(path) {
        runCatching {
            GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
    if (json != null) {
        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(json))
        LottieAnimation(
            composition = composition,
            modifier = Modifier.size(size),
            isPlaying = animate,
            iterations = if (animate) LottieConstants.IterateForever else 1,
        )
    }
}

@Composable
private fun WebpView(path: String, size: Dp) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    }
}
