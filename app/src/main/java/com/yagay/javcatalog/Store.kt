package com.yagay.javcatalog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Page(val url: String, val title: String, val visitedAt: Long)
data class Magnet(val uri: String, val label: String, val size: String, val pageUrl: String = "")

class Store(context: Context) : SQLiteOpenHelper(context, "catalog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks(url TEXT PRIMARY KEY,title TEXT NOT NULL,added INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(url TEXT PRIMARY KEY,title TEXT NOT NULL,visited INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE magnets(uri TEXT PRIMARY KEY,label TEXT NOT NULL,size TEXT,page_url TEXT,added INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun put(table: String, urlColumn: String, url: String, title: String, timeColumn: String) {
        val v = ContentValues().apply { put(urlColumn, url); put("title", title.take(300)); put(timeColumn, System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun history(url: String, title: String) = put("history", "url", url, title, "visited")
    fun bookmark(url: String, title: String) = put("bookmarks", "url", url, title, "added")
    fun hasBookmark(url: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url=?", arrayOf(url)).use { it.moveToFirst() }
    fun removeBookmark(url: String) { writableDatabase.delete("bookmarks", "url=?", arrayOf(url)) }
    fun removeHistory() { writableDatabase.delete("history", null, null) }
    fun removeMagnets() { writableDatabase.delete("magnets", null, null) }
    fun pages(table: String): List<Page> {
        require(table == "history" || table == "bookmarks")
        val timestamp = if (table == "history") "visited" else "added"
        return readableDatabase.rawQuery("SELECT url,title,$timestamp FROM $table ORDER BY $timestamp DESC LIMIT 500", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Page(cursor.getString(0), cursor.getString(1), cursor.getLong(2))) }
        }
    }
    fun saveMagnet(item: Magnet) {
        val cv = ContentValues().apply {
            put("uri", item.uri); put("label", item.label.take(300)); put("size", item.size.take(100));
            put("page_url", item.pageUrl); put("added", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("magnets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun magnets(): List<Magnet> = readableDatabase.rawQuery(
        "SELECT uri,label,size,page_url FROM magnets ORDER BY added DESC LIMIT 500", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(Magnet(cursor.getString(0), cursor.getString(1), cursor.getString(2) ?: "", cursor.getString(3) ?: "")) } }
}
