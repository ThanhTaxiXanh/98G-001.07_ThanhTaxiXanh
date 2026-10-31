package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase

/**
 * M5.3U: separate versioned SQLite snapshot for OSM restriction relations.
 * No road database schema migration is needed. Data is review-only.
 */
object OsmRestrictionSqliteStore {
    private const val DATABASE = "osm_turn_restrictions_v1.sqlite"
    private const val MAX_RELATIONS = 20_000

    data class Snapshot(val count: Int, val source: String)

    private class Helper(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DATABASE, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE restrictions (
                    relation_id TEXT PRIMARY KEY NOT NULL,
                    from_way TEXT NOT NULL,
                    to_way TEXT NOT NULL,
                    via_node TEXT NOT NULL,
                    restriction_kind TEXT NOT NULL,
                    source TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX restriction_via ON restrictions(via_node)")
            db.execSQL("CREATE TABLE snapshot_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported restriction database migration: $oldVersion to $newVersion")
        }
    }

    /** Replace the whole snapshot atomically; never merge different source extracts. */
    @Synchronized
    fun replace(
        context: Context, relations: List<OsmTurnRestrictionParser.Relation>,
        source: String
    ): Snapshot {
        require(source.isNotBlank() && source.length <= 256)
        require(relations.size <= MAX_RELATIONS)
        require(relations.all { it.source == source })
        require(relations.map { it.relationId }.toSet().size == relations.size)
        val db = Helper(context).writableDatabase
        try {
            db.beginTransaction()
            try {
                db.delete("restrictions", null, null)
                val insert = db.compileStatement("""
                    INSERT INTO restrictions(relation_id,from_way,to_way,via_node,
                                             restriction_kind,source)
                    VALUES (?,?,?,?,?,?)
                """.trimIndent())
                try {
                    for (r in relations) {
                        insert.clearBindings()
                        insert.bindString(1,r.relationId)
                        insert.bindString(2,r.fromWay)
                        insert.bindString(3,r.toWay)
                        insert.bindString(4,r.viaNode)
                        insert.bindString(5,r.restriction)
                        insert.bindString(6,r.source)
                        check(insert.executeInsert() != -1L)
                    }
                } finally {
                    insert.close()
                }
                db.execSQL("INSERT OR REPLACE INTO snapshot_meta(key,value) VALUES('source',?)",
                    arrayOf(source))
                db.execSQL("INSERT OR REPLACE INTO snapshot_meta(key,value) VALUES('importedAt',?)",
                    arrayOf(System.currentTimeMillis().toString()))
                db.setTransactionSuccessful()
                return Snapshot(relations.size,source)
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    @Synchronized
    fun read(context: Context): List<OsmTurnRestrictionParser.Relation> {
        val db = Helper(context).readableDatabase
        try {
            val rows = ArrayList<OsmTurnRestrictionParser.Relation>()
            db.rawQuery("""
                SELECT relation_id,from_way,to_way,via_node,restriction_kind,source
                FROM restrictions ORDER BY relation_id LIMIT ?
            """.trimIndent(), arrayOf((MAX_RELATIONS+1).toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    check(rows.size < MAX_RELATIONS) { "Restriction snapshot exceeds review limit" }
                    rows.add(OsmTurnRestrictionParser.Relation(
                        cursor.getString(0),cursor.getString(1),cursor.getString(2),
                        cursor.getString(3),cursor.getString(4),cursor.getString(5)))
                }
            }
            return rows
        } finally {
            db.close()
        }
    }
}
