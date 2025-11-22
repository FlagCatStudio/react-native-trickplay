import ExpoModulesCore
import AVFoundation
import UIKit

/// High-performance HLS I-Frame extraction module for iOS.
///
/// Architecture:
/// - Uses AVAssetImageGenerator with infinite tolerances for keyframe-only extraction
/// - Supports optional target dimensions with aspect ratio preservation
/// - LRU cache with 10-frame limit to prevent storage bloat
/// - Async/await for clean concurrency
public class ReactNativeTrickplayModule: Module {
  
  // MARK: - Module Definition
  
  public func definition() -> ModuleDefinition {
    Name("ReactNativeTrickplay")
    
    AsyncFunction("extractFrameAsync") { 
      (urlString: String, seconds: Double, targetWidth: Int?, targetHeight: Int?) -> [String: Any] in
      
      guard let url = URL(string: urlString) else {
        throw FrameExtractionError.invalidURL
      }
      
      return try await self.extractFrame(
        from: url,
        at: seconds,
        targetWidth: targetWidth,
        targetHeight: targetHeight
      )
    }
  }
  
  // MARK: - Frame Extraction
  
  /// Extract a video frame at the specified timestamp with optional target dimensions.
  ///
  /// - Parameters:
  ///   - url: HLS playlist URL (master or I-Frame playlist)
  ///   - seconds: Timestamp in seconds where to extract the frame
  ///   - targetWidth: Optional target width (preserves aspect ratio if height not provided)
  ///   - targetHeight: Optional target height (preserves aspect ratio if width not provided)
  /// - Returns: Dictionary with uri, width, height keys
  private func extractFrame(
    from url: URL,
    at seconds: Double,
    targetWidth: Int?,
    targetHeight: Int?
  ) async throws -> [String: Any] {
    
    let asset = AVURLAsset(url: url)
    
    // Verify asset is playable
    guard try await asset.load(.isPlayable), await asset.isPlayable else {
      throw FrameExtractionError.assetNotPlayable
    }
    
    let imageGenerator = AVAssetImageGenerator(asset: asset)
    
    // CRITICAL: Configure for keyframe extraction (I-Frames only)
    // Infinite tolerances = nearest keyframe, making extraction near-instant
    imageGenerator.appliesPreferredTrackTransform = true
    imageGenerator.requestedTimeToleranceBefore = .positiveInfinity
    imageGenerator.requestedTimeToleranceAfter = .positiveInfinity
    
    let requestedTime = CMTime(seconds: seconds, preferredTimescale: 600)
    let (cgImage, _) = try await imageGenerator.image(at: requestedTime)
    
    // Calculate target dimensions with aspect ratio preservation
    let sourceDimensions = (width: cgImage.width, height: cgImage.height)
    let targetDimensions = calculateTargetDimensions(
      sourceWidth: sourceDimensions.width,
      sourceHeight: sourceDimensions.height,
      targetWidth: targetWidth,
      targetHeight: targetHeight
    )
    
    // Resize if needed
    let finalImage: UIImage
    if targetDimensions.width != sourceDimensions.width || 
       targetDimensions.height != sourceDimensions.height {
      finalImage = try resizeImage(
        cgImage: cgImage,
        to: CGSize(width: targetDimensions.width, height: targetDimensions.height)
      )
    } else {
      finalImage = UIImage(cgImage: cgImage)
    }
    
    // Convert to JPEG
    guard let jpegData = finalImage.jpegData(compressionQuality: Constants.jpegQuality) else {
      throw FrameExtractionError.imageConversionFailed
    }
    
    // Save to file with LRU cleanup
    let fileURL = try saveFrameToFile(jpegData: jpegData)
    
    return [
      "uri": fileURL.absoluteString,
      "width": targetDimensions.width,
      "height": targetDimensions.height
    ]
  }
  
  // MARK: - Image Resizing
  
  /// Resize CGImage to target dimensions using high-quality interpolation.
  ///
  /// Uses UIGraphicsImageRenderer for hardware-accelerated scaling.
  private func resizeImage(cgImage: CGImage, to size: CGSize) throws -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1.0 // No retina scaling, exact pixel dimensions
    
    let renderer = UIGraphicsImageRenderer(size: size, format: format)
    
    let resizedImage = renderer.image { context in
      let rect = CGRect(origin: .zero, size: size)
      UIImage(cgImage: cgImage).draw(in: rect)
    }
    
    return resizedImage
  }
  
  /// Calculate target dimensions preserving aspect ratio.
  ///
  /// Logic:
  /// - Both dimensions provided → use as-is
  /// - Only width provided → calculate height preserving aspect ratio
  /// - Only height provided → calculate width preserving aspect ratio
  /// - Neither provided → use source dimensions
  private func calculateTargetDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int?,
    targetHeight: Int?
  ) -> (width: Int, height: Int) {
    
    switch (targetWidth, targetHeight) {
    case let (.some(width), .some(height)):
      // Both provided - use as-is
      return (width, height)
      
    case let (.some(width), .none):
      // Only width - calculate height preserving aspect ratio
      let aspectRatio = Double(sourceHeight) / Double(sourceWidth)
      let calculatedHeight = Int(Double(width) * aspectRatio)
      return (width, calculatedHeight)
      
    case let (.none, .some(height)):
      // Only height - calculate width preserving aspect ratio
      let aspectRatio = Double(sourceWidth) / Double(sourceHeight)
      let calculatedWidth = Int(Double(height) * aspectRatio)
      return (calculatedWidth, height)
      
    case (.none, .none):
      // Neither provided - use source dimensions
      return (sourceWidth, sourceHeight)
    }
  }
  
  // MARK: - File Management
  
  /// Save JPEG data to temporary file with unique timestamp-based filename.
  private func saveFrameToFile(jpegData: Data) throws -> URL {
    let tempDir = FileManager.default.temporaryDirectory
    let timestamp = Int(Date().timeIntervalSince1970 * 1000)
    let fileName = "trickplay_frame_\(timestamp).jpg"
    let fileURL = tempDir.appendingPathComponent(fileName)
    
    try jpegData.write(to: fileURL)
    
    // Trigger LRU cleanup
    cleanupOldFrames(in: tempDir)
    
    return fileURL
  }
  
  /// Remove old frame files keeping only the most recent frames.
  ///
  /// Uses LRU strategy based on file modification time.
  /// Silent fail - cleanup is best-effort and should not block extraction.
  private func cleanupOldFrames(in directory: URL) {
    do {
      let fileManager = FileManager.default
      let files = try fileManager.contentsOfDirectory(
        at: directory,
        includingPropertiesForKeys: [.contentModificationDateKey],
        options: .skipsHiddenFiles
      )
      
      // Filter only trickplay frame files
      let frameFiles = files.filter { url in
        url.lastPathComponent.hasPrefix("trickplay_frame_") &&
        url.pathExtension == "jpg"
      }
      
      // If under limit, no cleanup needed
      guard frameFiles.count > Constants.maxCachedFrames else { return }
      
      // Sort by modification date (oldest first)
      let sortedFiles = try frameFiles.sorted { url1, url2 in
        let date1 = try url1.resourceValues(forKeys: [.contentModificationDateKey])
          .contentModificationDate ?? .distantPast
        let date2 = try url2.resourceValues(forKeys: [.contentModificationDateKey])
          .contentModificationDate ?? .distantPast
        return date1 < date2
      }
      
      // Delete oldest files to keep only MAX_CACHED_FRAMES
      let filesToDelete = sortedFiles.dropLast(Constants.maxCachedFrames)
      for fileURL in filesToDelete {
        try? fileManager.removeItem(at: fileURL)
      }
    } catch {
      // Silent fail - cleanup is best-effort
      print("[ReactNativeTrickplay] Cleanup failed: \(error.localizedDescription)")
    }
  }
  
  // MARK: - Constants
  
  private enum Constants {
    static let jpegQuality: CGFloat = 0.80 // 80% quality optimal for thumbnails
    static let maxCachedFrames = 10 // Keep last 10 frames (20-400 KB total at 90x160)
  }
}

// MARK: - Error Types

enum FrameExtractionError: Error, CustomStringConvertible {
  case invalidURL
  case assetNotPlayable
  case imageConversionFailed
  case resizeFailed
  
  var description: String {
    switch self {
    case .invalidURL:
      return "Invalid URL provided"
    case .assetNotPlayable:
      return "Asset is not playable or cannot be loaded"
    case .imageConversionFailed:
      return "Failed to convert extracted frame to JPEG"
    case .resizeFailed:
      return "Failed to resize image to target dimensions"
    }
  }
}
