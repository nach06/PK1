package com.nacho.kartlog

import android.content.Intent
import android.net.Uri
import com.nacho.kartlog.domain.importing.SharedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedIntentsTest {
    @Test
    fun plainTextBecomesTextContent() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Vuelta 1:12.467")
        assertEquals(SharedContent.Text("Vuelta 1:12.467"), SharedIntents.toContent(intent))
    }

    @Test
    fun imageBecomesImageContent() {
        val uri = Uri.parse("content://media/external/images/1")
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(SharedContent.Image(uri.toString()), SharedIntents.toContent(intent))
    }

    @Test
    fun otherIntentsAreIgnored() {
        assertNull(SharedIntents.toContent(null))
        assertNull(SharedIntents.toContent(Intent(Intent.ACTION_MAIN)))
        assertNull(SharedIntents.toContent(Intent(Intent.ACTION_SEND).setType("text/plain"))) // sin texto
        assertNull(SharedIntents.toContent(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  ")))
    }
}
