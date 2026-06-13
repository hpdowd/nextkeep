package ie.dowd.nextkeep.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX analyzer that decodes QR codes from the camera's luminance (Y) plane
 * using ZXing — fully offline, no Google Play Services dependency. Invokes
 * [onResult] once with the decoded text, then ignores subsequent frames.
 */
class QrCodeAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    @Volatile
    private var finished = false

    override fun analyze(image: ImageProxy) {
        if (finished) {
            image.close()
            return
        }
        try {
            decode(image)?.let { text ->
                finished = true
                onResult(text)
            }
        } catch (_: NotFoundException) {
            // No QR code in this frame; try the next one.
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        // Size for the full stride so PlanarYUVLuminanceSource's bounds check
        // passes even when the last row carries no trailing padding.
        val data = ByteArray(rowStride * image.height)
        val buffer = plane.buffer
        buffer.get(data, 0, buffer.remaining())

        val source = PlanarYUVLuminanceSource(
            data,
            rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }
}
