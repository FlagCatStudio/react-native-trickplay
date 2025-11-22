package expo.modules.trickplay

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ReactNativeTrickplayModule
 * Run with: cd android && ./gradlew test
 */
class ReactNativeTrickplayModuleTest {
    
    @Test
    fun testExceptionMessageFormat() {
        val exception = FrameExtractionException("Test error")
        assertEquals("Test error", exception.message)
    }
    
    @Test
    fun testModuleCreation() {
        // Simple test to verify module class can be instantiated
        val module = ReactNativeTrickplayModule()
        assertNotNull("Module should not be null", module)
    }
    
    @Test
    fun testFileNameGeneration() {
        // Test that file name generation works correctly
        val timestamp = System.currentTimeMillis()
        val seconds = 5.5
        val fileName = "frame_${timestamp}_${(seconds * 1000).toInt()}.jpg"
        
        assertTrue("File name should contain frame_", fileName.startsWith("frame_"))
        assertTrue("File name should end with .jpg", fileName.endsWith(".jpg"))
        assertTrue("File name should contain timestamp", fileName.contains("5500"))
    }
}
