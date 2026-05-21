package dev.migs.scan.data

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads visible text out of an image. Production uses ML Kit's Latin
 * text recogniser; tests stub this so they don't have to mock Play Services.
 */
fun interface TextExtractor {
    suspend fun extractFrom(file: File): String

    companion object {
        fun mlKit(context: Context): TextExtractor = MlKitTextExtractor(context)

        /** Returns an empty string for every file. Useful in JVM tests. */
        val Empty: TextExtractor = TextExtractor { "" }
    }
}

private class MlKitTextExtractor(private val context: Context) : TextExtractor {

    // Lazy so that just constructing the extractor doesn't blow up on devices
    // / emulators where the OCR module hasn't finished downloading yet.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun extractFrom(file: File): String = try {
        val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Text recognition failed for ${file.name}", e)
                    cont.resume("")
                }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Could not run text recognition on ${file.name}", t)
        ""
    }

    companion object {
        private const val TAG = "MlKitTextExtractor"
    }
}
