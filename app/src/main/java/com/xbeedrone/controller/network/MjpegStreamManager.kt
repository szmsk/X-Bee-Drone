package com.xbeedrone.controller.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit

/**
 * Odbiera strumień MJPEG z kamery drona i udostępnia klatki jako Bitmap.
 *
 * Dron X-Bee 9.5 Fold wysyła MJPEG przez HTTP:
 *   http://192.168.10.1:8080/?action=stream
 *
 * Format MJPEG: multipart/x-mixed-replace z boundary separującym ramki JPEG.
 */
class MjpegStreamManager(private val streamUrl: String) {

    companion object {
        private const val TAG = "MjpegStream"
        private const val BOUNDARY_MARKER = "--"
        private const val CONTENT_LENGTH_HEADER = "Content-Length:"
        private val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) // Start Of Image
        private val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte()) // End Of Image
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _frameBitmap = MutableStateFlow<Bitmap?>(null)
    val frameBitmap: StateFlow<Bitmap?> = _frameBitmap

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private var streamJob: Job? = null
    private var frameCount = 0
    private var fpsTimer = System.currentTimeMillis()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun startStream() {
        stopStream()
        streamJob = scope.launch {
            _isStreaming.value = true
            while (isActive) {
                try {
                    connectAndStream()
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Stream error: ${e.message}, retrying...")
                    delay(2000)
                }
            }
            _isStreaming.value = false
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        _isStreaming.value = false
        _fps.value = 0
    }

    private suspend fun connectAndStream() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to MJPEG stream: $streamUrl")

        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Connection", "keep-alive")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                delay(2000)
                return@withContext
            }

            val body = response.body ?: return@withContext
            val inputStream = BufferedInputStream(body.byteStream(), 65536)
            val buffer = mutableListOf<Byte>()

            while (isActive) {
                val byte = inputStream.read()
                if (byte == -1) break

                buffer.add(byte.toByte())

                // Szukaj sekwencji JPEG SOI (FF D8) i EOI (FF D9)
                if (buffer.size >= 2) {
                    val last2 = buffer.takeLast(2)
                    if (last2[0] == JPEG_EOI[0] && last2[1] == JPEG_EOI[1]) {
                        // Znajdź SOI
                        val bytes = buffer.toByteArray()
                        val soiIdx = findSequence(bytes, JPEG_SOI)
                        if (soiIdx >= 0) {
                            val jpegBytes = bytes.copyOfRange(soiIdx, bytes.size)
                            decodeAndEmitFrame(jpegBytes)
                            buffer.clear()
                        }
                    }

                    // Ogranicz bufor żeby nie przepełnić pamięci
                    if (buffer.size > 500_000) {
                        buffer.clear()
                        Log.w(TAG, "Buffer overflow, clearing")
                    }
                }
            }
        }
    }

    private fun findSequence(data: ByteArray, sequence: ByteArray): Int {
        outer@ for (i in 0..data.size - sequence.size) {
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private suspend fun decodeAndEmitFrame(jpegBytes: ByteArray) {
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.RGB_565 // Szybsze dekodowanie
            }
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            if (bitmap != null) {
                _frameBitmap.value = bitmap

                // Aktualizuj FPS
                frameCount++
                val now = System.currentTimeMillis()
                if (now - fpsTimer >= 1000) {
                    _fps.value = frameCount
                    frameCount = 0
                    fpsTimer = now
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame decode error: ${e.message}")
        }
    }

    fun destroy() {
        stopStream()
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
    }
}
