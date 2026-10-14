package com.example

import com.example.network.BeamProtocol
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ExampleUnitTest {
  @Test
  fun testBeamProtocolHandshake() {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)

    BeamProtocol.writeHandshake(
        dos = dos,
        senderDeviceName = "SenderPhone",
        fileCount = 3,
        totalBytes = 104857600L
    )

    val bais = ByteArrayInputStream(baos.toByteArray())
    val dis = DataInputStream(bais)

    val handshake = BeamProtocol.readHandshake(dis)
    assertEquals("SenderPhone", handshake.senderName)
    assertEquals(3, handshake.fileCount)
    assertEquals(104857600L, handshake.totalBytes)
  }

  @Test
  fun testFileStartFraming() {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)

    val meta = BeamProtocol.FileMeta(
        name = "test_video.mp4",
        size = 52428800L,
        mimeType = "video/mp4"
    )

    BeamProtocol.writeFileStart(dos, 0, meta)

    val bais = ByteArrayInputStream(baos.toByteArray())
    val dis = DataInputStream(bais)

    val readMeta = BeamProtocol.readFileStart(dis)
    assertEquals("test_video.mp4", readMeta.name)
    assertEquals(52428800L, readMeta.size)
    assertEquals("video/mp4", readMeta.mimeType)
  }
}
