package com.mgalgs.wikipaper;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public final class DataManager {

	private static final String DATABASE_NAME = "WikiPaper";
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_TABLE = "Articles";

	private static final String KEY_ROW_ID = "_id";
	private static final String KEY_ARTICLE_SUMMARY = "summary";
	private static final String KEY_ARTICLE_TITLE = "title";
	private static final String KEY_USED = "used";

	private static final String ARTICLES_TABLE_CREATE = "CREATE TABLE "
			+ DATABASE_TABLE + " (" + KEY_ROW_ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_USED
			+ " INTEGER DEFAULT 0," + KEY_ARTICLE_TITLE + " TEXT NOT NULL,"
			+ KEY_ARTICLE_SUMMARY + " TEXT NOT NULL);";
	public int mLowRowsThreshold = 20;

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	private final Context mCtx;

	private static class DatabaseHelper extends SQLiteOpenHelper {
		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.i(WikiPaper.WP_LOGTAG, "In onCreate of DatabaseHelper");
			db.execSQL(ARTICLES_TABLE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub
			Log.i(WikiPaper.WP_LOGTAG, "Doing a DB upgrade on WikiPaper!");
		}
	}

	public DataManager(Context ctx) {
		mCtx = ctx;
	}

	public DataManager open() throws SQLException {
		Log.i(WikiPaper.WP_LOGTAG, "opening DataManager");
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		Log.i(WikiPaper.WP_LOGTAG, "closing DataManager");
		mDbHelper.close();
	}

	public void InsertSomeArticles(int n) {
		List<Article> alist = WikiParse.getRandomArticles(n);
//		Article[] alist = WikiParse.getRandomArticles(n);
		for (Article a : alist) {
			ContentValues cv = new ContentValues();
			cv.put(KEY_ARTICLE_SUMMARY, a.summary);
			cv.put(KEY_ARTICLE_TITLE, a.title);
			mDb.insert(DATABASE_TABLE, null, cv);
		}
	}

	public Article GetUnusedArticle() {
		Cursor c = null;
		try {
			c = mDb.query(true, DATABASE_TABLE, new String[] {
					KEY_ROW_ID, KEY_ARTICLE_SUMMARY, KEY_ARTICLE_TITLE,
					KEY_USED }, null, null, null, null, KEY_USED + " ASC", "1");

			if (c != null && c.getCount() > 0) {
				c.moveToFirst();

				// update the used count:
				String updateSql = String.format(
						"UPDATE %s SET %s=%s + 1 WHERE %s=%s", DATABASE_TABLE,
						KEY_USED, KEY_USED, KEY_ROW_ID,
						c.getString(c.getColumnIndex(KEY_ROW_ID)));
				mDb.execSQL(updateSql);

				// return the article
				Article a = new Article();
				a.summary = c.getString(c.getColumnIndex(KEY_ARTICLE_SUMMARY));
				a.title = c.getString(c.getColumnIndex(KEY_ARTICLE_TITLE));
				return a;
			} else {
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (c != null)
				c.close();
		}
	}

	public void maybeReplenishDb(int replenish_rate) {
		DbStats d = getDbStats();
		if (d == null)
			return;
		if (d.numUnusedArticles < mLowRowsThreshold) {
			Log.i(WikiPaper.WP_LOGTAG, "Need to replenish article supply...");
			InsertSomeArticles(replenish_rate);
		}
	}


	public DbStats getDbStats() {
		Cursor c = null;
		int nArticles = -1;
		try {
			c = mDb.query(false, DATABASE_TABLE, new String[] { KEY_ROW_ID },
					null, null, null, null, null, null);
			if (c == null)
				return null;
			nArticles = c.getCount();
		} catch (Exception e) {
			Log.e(WikiPaper.WP_LOGTAG, "Error getting nArticles stats");
			e.printStackTrace();
			return null;
		} finally {
			if (c != null)
				c.close();
		}

		try {
			c = mDb.query(false, DATABASE_TABLE, new String[] { KEY_USED },
					KEY_USED + " = 0", null, null, null, null, null);

			if (c == null)
				return null;
			int nUnusedArticles = c.getCount();
			c.deactivate();
			c.close();
			c = null;
			return new DbStats(nArticles, nUnusedArticles);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if(c != null)
				c.close();
		}
	}
	
	public void printEntireDb() {
		Cursor c = null;
		try {
			c = mDb.query(true, DATABASE_TABLE, new String[] { KEY_ROW_ID,
					KEY_ARTICLE_SUMMARY, KEY_ARTICLE_TITLE, KEY_USED }, null,
					null, null, null, null, null);
			if (c == null)
				return;
			if (!c.moveToFirst()) {
				Log.e(WikiPaper.WP_LOGTAG,
						"crap, nothing in the DB to print...");
			}
			do {
				Log.i(WikiPaper.WP_LOGTAG,
						"Here's one: \n"
								+ c.getString(c.getColumnIndex(KEY_ROW_ID))
								+ " "
								+ c.getString(c.getColumnIndex(KEY_USED))
								+ " "
								+ c.getString(c
										.getColumnIndex(KEY_ARTICLE_TITLE))
								+ " "
								+ c.getString(c
										.getColumnIndex(KEY_ARTICLE_SUMMARY)));
			} while (c.moveToNext());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (c != null)
				c.close();
		}
	}

}
