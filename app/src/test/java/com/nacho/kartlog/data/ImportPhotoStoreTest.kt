package com.nacho.kartlog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.AppContainer
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Sprint 6, D5: la foto de la hoja se guarda reducida en `imports/{id}.jpg` y se borra con su tanda y con «Borrar datos de rivales». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportPhotoStoreTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "imports").deleteRecursively()
        container = AppContainer(context)
    }

    @After
    fun tearDown() {
        Thread.sleep(200)
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "imports").deleteRecursively()
    }

    private fun sourceImage(width: Int, height: Int): String {
        val file = File(context.cacheDir, "foto_${width}x$height.png")
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF336699.toInt()) }
            .compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        return android.net.Uri.fromFile(file).toString()
    }

    private suspend fun newSession(): Long {
        val circuit = container.circuitRepository.add("Zuera", 1_100)
        return container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))
    }

    @Test
    fun savesAReducedJpegNamedAfterTheSession() = runBlocking {
        val id = newSession()
        assertTrue(container.importPhotoStore.save(id, sourceImage(4000, 3000)))
        val path = container.importPhotoStore.pathOf(id)
        assertNotNull(path)
        assertEquals(File(context.filesDir, "imports/$id.jpg").absolutePath, path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val head = File(path!!).inputStream().use { it.readNBytes(2) }
        assertEquals("JPEG (FF D8)", listOf(0xFF.toByte(), 0xD8.toByte()), head.toList())
        assertTrue("reducida a ≤ 2048 px: ${bounds.outWidth}×${bounds.outHeight}", maxOf(bounds.outWidth, bounds.outHeight) <= 2048)
    }

    @Test
    fun anUnreadableSourceSavesNothing() = runBlocking {
        val id = newSession()
        assertFalse(container.importPhotoStore.save(id, "file:///no/existe.jpg"))
        assertNull(container.importPhotoStore.pathOf(id))
    }

    @Test
    fun thePhotoIsDeletedWithItsSession() = runBlocking {
        val id = newSession()
        container.importPhotoStore.save(id, sourceImage(800, 600))
        assertNotNull(container.importPhotoStore.pathOf(id))
        container.sessionRepository.delete(id)
        assertNull(container.importPhotoStore.pathOf(id))
    }

    @Test
    fun clearingRivalDataDeletesEveryPhoto_butNotTheSessions() = runBlocking {
        val a = newSession()
        val b = container.sessionRepository.create(NewSession(container.circuitRepository.add("Alonso", 1_400), 1_760_100_000_000, SessionType.RACE, null, null, null, null, TrackCondition.DRY, ""))
        container.importPhotoStore.save(a, sourceImage(800, 600))
        container.importPhotoStore.save(b, sourceImage(800, 600))
        container.importRepository.clearRivalData()
        assertNull(container.importPhotoStore.pathOf(a))
        assertNull(container.importPhotoStore.pathOf(b))
        // Las tandas siguen ahí.
        assertEquals(2, container.sessionRepository.observeSummaries().let { it.let { flow -> kotlinx.coroutines.runBlocking { flow.first() } }.size })
    }

    @Test
    fun theSettingIsOnByDefault_andPersists() {
        assertTrue(container.userSettings.saveImportPhoto.value)
        container.userSettings.setSaveImportPhoto(false)
        assertFalse(AppContainer(context).userSettings.saveImportPhoto.value)
    }
}
