package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileUtils
import com.example.util.QrCodeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun testAppNameResource() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BeamDrop", appName)
  }

  @Test
  fun testQrCodeGenerationAndParsing() {
    val payloadString = QrCodeUtil.createPayloadString(
        ip = "192.168.43.1",
        port = 8988,
        deviceName = "Pixel Device",
        ssid = "BeamDrop_Hotspot"
    )
    val parsed = QrCodeUtil.parsePayload(payloadString)
    assertNotNull(parsed)
    assertEquals("192.168.43.1", parsed?.ip)
    assertEquals(8988, parsed?.port)
    assertEquals("Pixel Device", parsed?.deviceName)
    assertEquals("BeamDrop_Hotspot", parsed?.ssid)
  }

  @Test
  fun testFileSizingFormat() {
    assertEquals("0 B", FileUtils.formatFileSize(0))
    assertEquals("1 KB", FileUtils.formatFileSize(1024))
    assertEquals("1.5 MB", FileUtils.formatFileSize((1.5 * 1024 * 1024).toLong()))
    assertEquals("2 GB", FileUtils.formatFileSize(2L * 1024 * 1024 * 1024))
  }
}
