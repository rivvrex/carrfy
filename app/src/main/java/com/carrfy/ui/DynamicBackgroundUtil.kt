package com.carrfy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dominantColorCache = mutableMapOf<String, Color>()

suspend fun extractDominantColor(context: Context, imageUrl: String?, fallback: Color = Color(0xFF512DA8)): Color {
    if (imageUrl == null) return fallback
    dominantColorCache[imageUrl]?.let { return it }

    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val computed = if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val scaled = if (bitmap.width > 96 || bitmap.height > 96) {
                    Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                } else {
                    bitmap
                }
                val palette = Palette.from(scaled)
                    .maximumColorCount(16)
                    .generate()
                Color(palette.getDominantColor(fallback.value.toInt()))
            } else {
                fallback
            }
        } else {
            fallback
        }

        dominantColorCache[imageUrl] = computed
        computed
    }
} 