package com.nacho.kartlog.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 8, D2: el borrado se confirma a los 8 s, se puede deshacer antes y solo hay uno deshacible a la vez. */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingDeletionsTest {
    private val scope = TestScope()
    private val manager = PendingDeletions(scope)
    private val committed = mutableListOf<Long>()

    private fun schedule(id: Long, kind: PendingDeletions.Kind = PendingDeletions.Kind.LAP) =
        manager.schedule(kind, id, arg = id.toInt()) { committed += id }

    @Test
    fun theDeletionIsHiddenAtOnce_butOnlyCommittedAfterEightSeconds() {
        schedule(1)
        scope.runCurrent()
        assertEquals(listOf(1L), manager.hiddenItems.value.map { it.id })
        assertTrue(committed.isEmpty())
        scope.advanceTimeBy(7_900); scope.runCurrent()
        assertTrue("antes de 8 s no se borra", committed.isEmpty())
        assertEquals(1L, manager.current.value?.id)
        scope.advanceTimeBy(200); scope.runCurrent()
        assertEquals(listOf(1L), committed)
        assertNull(manager.current.value)
        assertTrue("tras confirmarse deja de estar oculto", manager.hiddenItems.value.isEmpty())
    }

    @Test
    fun undoBeforeTheWindowEnds_neverCommits_andShowsTheItemAgain() {
        val p = schedule(2)
        scope.advanceTimeBy(3_000); scope.runCurrent()
        assertTrue(manager.undo(p.token))
        assertTrue(manager.hiddenItems.value.isEmpty())
        assertNull(manager.current.value)
        scope.advanceTimeBy(20_000); scope.runCurrent()
        assertTrue("deshecho: no se borra nunca", committed.isEmpty())
    }

    @Test
    fun aSecondDeletionCommitsTheFirstImmediately_andOnlyTheLastCanBeUndone() {
        val first = schedule(1)
        scope.advanceTimeBy(1_000); scope.runCurrent()
        val second = schedule(2, PendingDeletions.Kind.SESSION)
        scope.runCurrent()
        assertEquals(listOf(1L), committed)
        assertFalse("la primera ya no es deshacible", manager.undo(first.token))
        assertEquals(2L, manager.current.value?.id)
        scope.advanceTimeBy(8_100); scope.runCurrent()
        assertEquals(listOf(1L, 2L), committed)
        assertFalse(manager.undo(second.token))
    }

    @Test
    fun aFailingCommitStillReleasesTheHiddenItem() {
        manager.schedule(PendingDeletions.Kind.LAP, 3) { error("BD cerrada") }
        scope.advanceTimeBy(8_100); scope.runCurrent()
        assertTrue(manager.hiddenItems.value.isEmpty())
    }
}
