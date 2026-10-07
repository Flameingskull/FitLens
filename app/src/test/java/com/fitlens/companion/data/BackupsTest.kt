package com.fitlens.companion.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `.flexnotes` backup and restore (#40): the archive written by [Backups.writeArchive], read back and checked by
 * [Backups.unpack], and put in place by [Backups.installDatabase] — the same three steps a real backup and restore
 * take. A restored backup gives back exactly what was saved; an older backup is upgraded when it opens; a damaged,
 * foreign or newer file is refused before anything live is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupsTest {

    private lateinit var app: Application
    private lateinit var work: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
        work = File(app.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        app.deleteDatabase(Db.NAME)
        work.deleteRecursively()
    }

    private val liveDb: File get() = app.getDatabasePath(Db.NAME)

    /** Writes an archive of [dbFile] (plus [photos]) and returns its bytes. */
    private fun archive(dbFile: File?, photos: List<File> = emptyList(), dataOnly: Boolean = false): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val manifest = JSONObject().apply { put("format", 1); put("app", "FlexNotes"); if (dataOnly) put("dataOnly", true) }
        if (dbFile != null) {
            Backups.writeArchive(out, manifest, dbFile, photos)
        } else {
            // An archive with no database in it.
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun unpack(bytes: ByteArray?): Backups.Unpacked {
        val stage = File(work, "stage").apply { deleteRecursively(); mkdirs() }
        val photos = File(stage, "photos").apply { mkdirs() }
        return Backups.unpack(bytes?.let { ByteArrayInputStream(it) }, File(stage, "fitlens.db"), photos)
    }

    private val stagedDb: File get() = File(work, "stage/fitlens.db")

    @Test
    fun aRestoredBackupGivesBackWhatWasSaved() {
        Db(app).use { h ->
            val w = h.writableDatabase
            val ex = w.row("exercise", "name" to "Deadlift", "category_id" to 0L, "source" to Sources.FLEXNOTES)
            w.row("workout_set", "exercise_id" to ex, "date" to "2026-02-01", "weight" to 180.0, "reps" to 3, "source" to Sources.FLEXNOTES)
            w.row("workout_set", "exercise_id" to ex, "date" to "2026-02-01", "weight" to 190.0, "reps" to 1, "source" to Sources.FLEXNOTES)
            w.row("mrecord", "name" to "Bodyweight", "unit" to "kg", "date" to "2026-02-01", "value" to 90.5, "source" to Sources.FLEXNOTES)
            w.row("exercise_comment", "date" to "2026-02-01", "exercise_id" to ex, "comment" to "Belt on", "source" to Sources.FLEXNOTES)
        }
        val photo = File(work, "front.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val saved = archive(liveDb, listOf(photo))

        // After the backup, the data changes.
        Db(app).use { h -> h.writableDatabase.execSQL("DELETE FROM workout_set") }

        val u = unpack(saved)
        assertNull(u.problem)
        assertEquals(1, u.photos)
        assertEquals(false, u.dataOnly)
        assertTrue(File(work, "stage/photos/front.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        Backups.installDatabase(app, stagedDb)

        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(2, db.count("SELECT COUNT(*) FROM workout_set"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE weight=190 AND reps=1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM mrecord WHERE value=90.5"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise_comment WHERE comment='Belt on'"))
        }
    }

    @Test
    fun aSafetyCopyIsMarkedDataOnly() {
        Db(app).use { it.writableDatabase }
        val u = unpack(archive(liveDb, dataOnly = true))
        assertNull(u.problem)
        assertTrue(u.dataOnly)
        assertEquals(0, u.photos)
    }

    @Test
    fun anOlderBackupIsUpgradedWhenItOpens() {
        val old = File(work, "old.db")
        OldSchemas.create(old, 2, OldSchemas.V2) { OldSchemas.fillV2(it) }
        val u = unpack(archive(old))
        assertNull(u.problem)
        Backups.installDatabase(app, stagedDb)
        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(Db.VERSION, db.version)
            assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set WHERE source='fitnotes'"))
            assertTrue(db.hasTable("exercise_comment"))
        }
    }

    @Test
    fun aBackupFromANewerFlexNotesIsRefused() {
        val newer = File(work, "newer.db")
        OldSchemas.create(newer, Db.VERSION + 1, OldSchemas.V2)
        val u = unpack(archive(newer))
        assertEquals(Backups.Refusal.NEWER, u.problem)
    }

    @Test
    fun aDamagedOrForeignFileIsRefused() {
        // A database without FlexNotes's tables.
        val foreign = File(work, "foreign.db")
        OldSchemas.create(foreign, 1, listOf("CREATE TABLE something(id INTEGER)"))
        assertEquals(Backups.Refusal.DAMAGED, unpack(archive(foreign)).problem)
        // A zip with no database in it.
        assertEquals(Backups.Refusal.NOT_A_BACKUP, unpack(archive(null)).problem)
        // Nothing to read at all.
        assertEquals(Backups.Refusal.CANT_OPEN, unpack(null).problem)
    }

    @Test
    fun aRefusedBackupLeavesTheLiveDataAlone() {
        Db(app).use { h ->
            val w = h.writableDatabase
            w.row("mrecord", "name" to "Bodyweight", "unit" to "kg", "date" to "2026-03-01", "value" to 77.0, "source" to Sources.FLEXNOTES)
        }
        val newer = File(work, "newer.db")
        OldSchemas.create(newer, Db.VERSION + 1, OldSchemas.V2)
        val u = unpack(archive(newer))
        assertTrue(u.problem != null)

        // The restore stops at the error, so the live database is never replaced.
        Db(app).use { h -> assertEquals(1, h.writableDatabase.count("SELECT COUNT(*) FROM mrecord WHERE value=77")) }
    }
}
