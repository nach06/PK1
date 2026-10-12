package com.nacho.kartlog.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/** Sprint 7, A1: el listado de muestras tolera extensiones, mayúsculas, dobles extensiones y subcarpetas. */
class SampleImagesTest {
    /** Assets falsos: un mapa carpeta → hijos (los ficheros no tienen entrada). */
    private class FakeAssets(private val tree: Map<String, List<String>>) : AssetSource {
        override fun list(path: String): Array<String>? = tree[path]?.toTypedArray()
        override fun open(path: String): InputStream = ByteArrayInputStream(path.toByteArray())
    }

    private fun images(vararg names: String, sub: Map<String, List<String>> = emptyMap(), cache: File = File("build/tmp")) =
        SampleImages(FakeAssets(mapOf("muestras" to names.toList()) + sub), cache, projectDir = "C:/Proyectos/pk1")

    @Test
    fun acceptsAnyImageExtension_caseInsensitive_andDoubleExtensions() {
        val s = images("muestra_a.png", "muestra_b.JPEG", "c.WebP", "d.bmp", "muestra_a.png.png", "foto.jpg.JPG", "LEEME.txt", "notas.docx", "sin_extension")
        assertEquals(
            listOf("c.WebP", "d.bmp", "foto.jpg.JPG", "muestra_a.png", "muestra_a.png.png", "muestra_b.JPEG"),
            s.names(),
        )
    }

    @Test
    fun findsImagesInNestedFolders() {
        val s = images("sub", "LEEME.txt", sub = mapOf("muestras/sub" to listOf("muestra_a.png", "otra")), )
        assertEquals(listOf("sub/muestra_a.png"), s.names())
    }

    @Test
    fun diagnosisListsEverythingWithExactNames_andTheExpectedFolder() {
        val s = images("LEEME.txt", "muestra_a.png.txt")
        val d = s.diagnosis()
        assertEquals(listOf("LEEME.txt", "muestra_a.png.txt"), d.entries)
        assertEquals(0, d.imageCount)
        assertEquals("C:/Proyectos/pk1/app/src/debug/assets/muestras", d.expectedDir)
        assertTrue(s.names().isEmpty())
    }

    @Test
    fun aMissingFolderIsAnEmptyDiagnosis_notACrash() {
        val s = SampleImages(FakeAssets(emptyMap()), File("build/tmp"), projectDir = "/x")
        assertTrue(s.allEntries().isEmpty())
        assertEquals(0, s.diagnosis().entries.size)
        assertFalse(SampleImages.isSampleImage("LEEME.txt"))
    }
}
