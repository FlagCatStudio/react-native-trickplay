package expo.modules.trickplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Custom exception for frame extraction failures.
 */
class FrameExtractionException(message: String) : Exception(message)

/**
 * High-performance frame extractor for HLS I-Frame playlists.
 * 
 * This class provides optimized frame extraction from HLS video streams by:
 * - Reusing a single ExoPlayer instance across extractions
 * - Automatically selecting the lowest quality track (360p max)
 * - Seeking to nearest I-Frames for instant frame access
 * - Using PixelCopy API for hardware-accelerated bitmap extraction
 * 
 * Thread-safety: All operations are serialized via Mutex to prevent race conditions.
 * 
 * @param context Android context for ExoPlayer initialization
 */
@UnstableApi
class FrameExtractor(private val context: Context) {

    // --- State ---
    
    private var player: ExoPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var currentUrl: String? = null
    
    /**
     * Mutex to serialize frame extractions.
     * ExoPlayer is not thread-safe for concurrent seek operations.
     */
    private val extractionMutex = Mutex()
    
    /**
     * Handler for main thread operations (required by ExoPlayer and PixelCopy).
     */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // --- Public API ---
    
    /**
     * Extracts a single frame from the video at the specified timestamp.
     * 
     * @param urlString HLS playlist URL (master or I-Frame playlist)
     * @param seconds Timestamp in seconds where to extract the frame
     * @param targetWidth Optional target width (null = use video dimensions)
     * @param targetHeight Optional target height (null = use video dimensions)
     * @return Bitmap of the extracted frame with target dimensions if specified
     * @throws FrameExtractionException if extraction fails
     */
    suspend fun extractFrame(
        urlString: String,
        seconds: Double,
        targetWidth: Int? = null,
        targetHeight: Int? = null
    ): Bitmap = extractionMutex.withLock {
        withContext(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            logDebug("Starting extraction at ${seconds}s")
            
            try {
                ensurePlayerInitialized()
                val player = requirePlayer()
                
                if (shouldReloadMedia(urlString)) {
                    loadMedia(player, urlString, startTime)
                }
                
                seekToPosition(player, seconds, startTime)
                waitForFrameRendered(player, startTime)
                
                val bitmap = extractBitmapFromPlayer(player, startTime, targetWidth, targetHeight)
                logDebug("Extraction completed in ${System.currentTimeMillis() - startTime}ms")
                
                bitmap
            } catch (e: Exception) {
                logError("Extraction failed", e)
                release() // Reset player on critical errors
                throw if (e is FrameExtractionException) e else FrameExtractionException(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Releases all resources. Must be called when extractor is no longer needed.
     * Safe to call multiple times.
     */
    fun release() {
        mainHandler.post {
            surface?.release()
            surfaceTexture?.release()
            player?.release()
            
            player = null
            surface = null
            surfaceTexture = null
            currentUrl = null
        }
    }
    
    // --- Initialization ---
    
    private fun ensurePlayerInitialized() {
        if (player != null) return
        
        val trackSelector = createOptimizedTrackSelector()
        
        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                // Seek to nearest I-Frame for instant extraction
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                // Disable audio to save resources
                volume = 0f
            }
        
        surfaceTexture = SurfaceTexture(false).apply {
            setDefaultBufferSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }
        
        surface = Surface(surfaceTexture).also { 
            player?.setVideoSurface(it)
        }
    }
    
    private fun createOptimizedTrackSelector(): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Max resolution: 640x640 works for both landscape (640x360) and portrait (360x640)
                    .setMaxVideoSize(MAX_DIMENSION, MAX_DIMENSION)
                    // Always prefer lowest bitrate for thumbnails
                    .setForceLowestBitrate(true)
                    .build()
            )
        }
    }
    
    // --- Media Loading ---
    
    private fun shouldReloadMedia(url: String): Boolean = currentUrl != url
    
    private suspend fun loadMedia(player: ExoPlayer, url: String, startTime: Long) {
        logDebug("[${elapsed(startTime)}ms] Loading new media")
        
        // Reset player to avoid cache issues
        player.stop()
        player.clearMediaItems()
        
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        
        logDebug("[${elapsed(startTime)}ms] Waiting for player ready...")
        waitForPlayerReady(player)
        
        currentUrl = url
        
        // Log all available tracks for debugging
        logAllAvailableTracks(player)
        
        // Log selected track info and adjust surface
        logSelectedTrack(player.videoFormat)
        adjustSurfaceSize(player.videoFormat)
    }
    
    private fun adjustSurfaceSize(format: Format?) {
        if (format != null && format.width > 0 && format.height > 0) {
            surfaceTexture?.setDefaultBufferSize(format.width, format.height)
        }
    }
    
    // --- Seeking ---
    
    private fun seekToPosition(player: ExoPlayer, seconds: Double, startTime: Long) {
        val positionMs = (seconds * 1000).toLong()
        logDebug("[${elapsed(startTime)}ms] Seeking to ${positionMs}ms")
        player.seekTo(positionMs)
    }
    
    // --- Bitmap Extraction ---
    
    private suspend fun extractBitmapFromPlayer(
        player: ExoPlayer,
        startTime: Long,
        targetWidth: Int?,
        targetHeight: Int?
    ): Bitmap {
        val format = player.videoFormat
        val sourceWidth = format?.width ?: DEFAULT_WIDTH
        val sourceHeight = format?.height ?: DEFAULT_HEIGHT
        
        // Calculate final dimensions with aspect ratio preservation
        val (finalWidth, finalHeight) = calculateTargetDimensions(
            sourceWidth,
            sourceHeight,
            targetWidth,
            targetHeight
        )
        
        logDebug("[${elapsed(startTime)}ms] Extracting ${sourceWidth}x${sourceHeight} -> ${finalWidth}x${finalHeight} bitmap")
        
        // Extract at source resolution
        val sourceBitmap = extractBitmapFromSurface(requireSurface(), sourceWidth, sourceHeight)
        
        // Resize if dimensions are different
        return if (finalWidth != sourceWidth || finalHeight != sourceHeight) {
            logDebug("[${elapsed(startTime)}ms] Resizing bitmap to preserve aspect ratio")
            val resized = Bitmap.createScaledBitmap(sourceBitmap, finalWidth, finalHeight, true)
            sourceBitmap.recycle()
            resized
        } else {
            sourceBitmap
        }
    }
    
    /**
     * Calculate target dimensions preserving aspect ratio.
     * If both dimensions are provided, use them as-is.
     * If only one is provided, calculate the other to preserve aspect ratio.
     * If neither is provided, use source dimensions.
     */
    private fun calculateTargetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int?,
        targetHeight: Int?
    ): Pair<Int, Int> {
        return when {
            // Both dimensions provided - use as-is
            targetWidth != null && targetHeight != null -> Pair(targetWidth, targetHeight)
            
            // Only width provided - calculate height preserving aspect ratio
            targetWidth != null -> {
                val aspectRatio = sourceHeight.toFloat() / sourceWidth.toFloat()
                val calculatedHeight = (targetWidth * aspectRatio).toInt()
                Pair(targetWidth, calculatedHeight)
            }
            
            // Only height provided - calculate width preserving aspect ratio
            targetHeight != null -> {
                val aspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
                val calculatedWidth = (targetHeight * aspectRatio).toInt()
                Pair(calculatedWidth, targetHeight)
            }
            
            // Neither provided - use source dimensions
            else -> Pair(sourceWidth, sourceHeight)
        }
    }
    
    private suspend fun extractBitmapFromSurface(surface: Surface, width: Int, height: Int): Bitmap =
        suspendCancellableCoroutine { cont ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            try {
                PixelCopy.request(
                    surface,
                    bitmap,
                    { result ->
                        when {
                            result == PixelCopy.SUCCESS && cont.isActive -> cont.resume(bitmap)
                            cont.isActive -> cont.resumeWithException(
                                FrameExtractionException("PixelCopy failed with code: $result")
                            )
                        }
                    },
                    mainHandler
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    
    // --- Async Helpers ---
    
    private suspend fun waitForPlayerReady(player: ExoPlayer) = suspendCancellableCoroutine<Unit> { cont ->
        if (player.playbackState == Player.STATE_READY) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when {
                    state == Player.STATE_READY -> {
                        player.removeListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                    state == Player.STATE_IDLE -> {
                        player.removeListener(this)
                        if (cont.isActive) {
                            cont.resumeWithException(FrameExtractionException("Player went to IDLE state"))
                        }
                    }
                }
            }
        }
        
        player.addListener(listener)
        
        // Timeout to prevent infinite hang
        mainHandler.postDelayed({
            if (cont.isActive) {
                player.removeListener(listener)
                cont.resumeWithException(FrameExtractionException("Player ready timeout"))
            }
        }, PLAYER_READY_TIMEOUT_MS)
        
        cont.invokeOnCancellation { player.removeListener(listener) }
    }
    
    private suspend fun waitForFrameRendered(player: ExoPlayer, startTime: Long) = suspendCancellableCoroutine<Unit> { cont ->
        logDebug("[${elapsed(startTime)}ms] Waiting for frame render...")
        
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                player.removeListener(this)
                if (cont.isActive) cont.resume(Unit)
            }
        }
        
        player.addListener(listener)
        
        // Fallback timeout for edge cases (end of video, seek failures)
        mainHandler.postDelayed({
            if (cont.isActive) {
                player.removeListener(listener)
                cont.resume(Unit)
            }
        }, FRAME_RENDER_TIMEOUT_MS)
        
        cont.invokeOnCancellation { player.removeListener(listener) }
    }
    
    // --- Utility ---
    
    private fun requirePlayer(): ExoPlayer = 
        player ?: throw FrameExtractionException("Player not initialized")
    
    private fun requireSurface(): Surface = 
        surface ?: throw FrameExtractionException("Surface not initialized")
    
    private fun elapsed(startTime: Long): Long = System.currentTimeMillis() - startTime
    
    // --- Logging ---
    
    private fun logDebug(message: String) {
        if (DEBUG) Log.d(TAG, message)
    }
    
    private fun logError(message: String, error: Exception) {
        Log.e(TAG, "$message: ${error.message}", error)
    }
    
    private fun logAllAvailableTracks(player: ExoPlayer) {
        if (!DEBUG) return
        
        try {
            val trackGroups = player.currentTracks.groups
            Log.i(TAG, "\n========== ALL AVAILABLE VIDEO TRACKS ==========")
            trackGroups.forEachIndexed { index, group ->
                if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        Log.i(TAG, "Track $index.$i: ${format.width}x${format.height} @ ${format.bitrate / 1000}kbps (${format.codecs})")
                    }
                }
            }
            Log.i(TAG, "================================================\n")
        } catch (e: Exception) {
            Log.w(TAG, "Could not log available tracks: ${e.message}")
        }
    }
    
    private fun logSelectedTrack(format: Format?) {
        if (!DEBUG || format == null) return
        
        Log.i(TAG, """
            |
            |========== SELECTED VIDEO TRACK ==========
            |Resolution: ${format.width}x${format.height}
            |Bitrate: ${format.bitrate / 1000} kbps
            |Frame Rate: ${format.frameRate} fps
            |Codec: ${format.codecs}
            |Sample MIME: ${format.sampleMimeType}
            |===========================================
        """.trimMargin())
    }
    
    // --- Constants ---
    
    private companion object {
        private const val TAG = "FrameExtractor"
        private const val DEBUG = true // Set to false for production
        
        // Track selection constraints
        private const val MAX_DIMENSION = 640 // Max width or height (360p for 16:9)
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 360
        
        // Timeouts
        private const val PLAYER_READY_TIMEOUT_MS = 5000L
        private const val FRAME_RENDER_TIMEOUT_MS = 500L
    }
}
