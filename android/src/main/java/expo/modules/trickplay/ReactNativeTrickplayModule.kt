package expo.modules.trickplay

import android.graphics.Bitmap
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Expo module for extracting frames from HLS video streams.
 * 
 * This module provides TrickPlay functionality by extracting individual frames
 * from HLS I-Frame playlists at specified timestamps, optimized for video scrubbing.
 */
class ReactNativeTrickplayModule : Module() {
    
    private var frameExtractor: FrameExtractor? = null

    override fun definition() = ModuleDefinition {
        Name("ReactNativeTrickplay")

        OnCreate {
            initializeExtractor()
        }

        OnDestroy {
            cleanupExtractor()
        }

        AsyncFunction("extractFrameAsync") { urlString: String, seconds: Double, targetWidth: Int?, targetHeight: Int? ->
            extractFrame(urlString, seconds, targetWidth, targetHeight)
        }
    }
    
    // --- Private helpers ---
    
    private fun initializeExtractor() {
        val context = appContext.reactContext ?: return
        frameExtractor = FrameExtractor(context)
    }
    
    private fun cleanupExtractor() {
        frameExtractor?.release()
        frameExtractor = null
    }
    
    private fun extractFrame(
        urlString: String,
        seconds: Double,
        targetWidth: Int?,
        targetHeight: Int?
    ): Map<String, Any> {
        val context = appContext.reactContext 
            ?: throw FrameExtractionException("React context is not available")
        
        // Ensure extractor exists (lazy init if needed)
        val extractor = frameExtractor 
            ?: FrameExtractor(context).also { frameExtractor = it }
        
        // Extract frame bitmap (blocking coroutine call)
        val bitmap = runBlocking {
            extractor.extractFrame(urlString, seconds, targetWidth, targetHeight)
        }
        
        // Save to disk and return metadata
        return saveBitmapAndReturnMetadata(bitmap, context.cacheDir)
    }
    
    private fun saveBitmapAndReturnMetadata(bitmap: Bitmap, cacheDir: File): Map<String, Any> {
        val file = createTempFrameFile(cacheDir)
        
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
        
        return mapOf(
            "uri" to "file://${file.absolutePath}",
            "width" to bitmap.width,
            "height" to bitmap.height
        )
    }
    
    private fun createTempFrameFile(cacheDir: File): File {
        val timestamp = System.currentTimeMillis()
        val file = File(cacheDir, "trickplay_frame_$timestamp.jpg")
        
        // Cleanup old frames (keep only last MAX_CACHED_FRAMES)
        cleanupOldFrames(cacheDir)
        
        return file
    }
    
    /**
     * Remove old frame files keeping only the most recent MAX_CACHED_FRAMES.
     * Uses LRU strategy based on file modification time.
     */
    private fun cleanupOldFrames(cacheDir: File) {
        try {
            val frameFiles = cacheDir.listFiles { file ->
                file.name.startsWith("trickplay_frame_") && file.name.endsWith(".jpg")
            } ?: return
            
            // If we're under the limit, no cleanup needed
            if (frameFiles.size <= MAX_CACHED_FRAMES) return
            
            // Sort by last modified time (oldest first)
            val sortedFiles = frameFiles.sortedBy { it.lastModified() }
            
            // Delete oldest files to keep only MAX_CACHED_FRAMES
            val filesToDelete = sortedFiles.dropLast(MAX_CACHED_FRAMES)
            filesToDelete.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            // Silent fail - cleanup is best-effort
            android.util.Log.w("ReactNativeTrickplay", "Failed to cleanup old frames: ${e.message}")
        }
    }
    
    private companion object {
        private const val JPEG_QUALITY = 80 // 80% quality is optimal for thumbnails
        private const val MAX_CACHED_FRAMES = 10 // Keep last 10 frames (20-400 KB total at 90x160)
    }
}
