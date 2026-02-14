package org.rw3h4.echonotex.util.note

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult

class CoilImageGetter(
    private val context: Context,
    private val textView: TextView
) : Html.ImageGetter {

    private val imageLoader = ImageLoader(context)

    override fun getDrawable(source: String?): Drawable {
        val placeholder = BitmapDrawablePlaceholder()

        val request = ImageRequest.Builder(context)
            .data(source)
            .listener(onSuccess = { _, result ->
                val image = result.image
                if (image is BitmapImage) {
                    val bitmap = image.bitmap
                    val bitmapDrawable = BitmapDrawable(context.resources, bitmap)

                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val scale = screenWidth.toFloat() / bitmap.width.toFloat()
                    val newHeight = (bitmap.height * scale).toInt()

                    bitmapDrawable.setBounds(0, 0, screenWidth, newHeight)
                    placeholder.innerDrawable = bitmapDrawable

                    textView.post { textView.text = textView.text }
                }
            })
            .build()

        imageLoader.enqueue(request)
        return placeholder
    }

    private class BitmapDrawablePlaceholder : BitmapDrawable() {
        var innerDrawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            innerDrawable?.draw(canvas)
        }
    }
}
