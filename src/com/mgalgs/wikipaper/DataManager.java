package com.mgalgs.wikipaper;

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
	private static final String KEY_ARTICLE_SUMMARY = "article_summary";
	private static final String KEY_USED = "used";
	
	private static final String ARTICLES_TABLE_CREATE = "CREATE TABLE "
			+ DATABASE_TABLE + " ("
			+ KEY_ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ KEY_USED + " INTEGER DEFAULT 0,"
			+ KEY_ARTICLE_SUMMARY + " TEXT NOT NULL);";
	private static final int LOW_ROWS_THRESHOLD = 5;

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

	private void InsertSomeArticles(int n) {
		for (int i = 0; i < n; i++) {
			Log.i(WikiPaper.WP_LOGTAG,
					"inserting random article "
			+ Integer.toString(i+1) + "/" + Integer.toString(n));
			ContentValues cv = new ContentValues();
			cv.put(KEY_ARTICLE_SUMMARY, WikiParse.getRandomArticleSummary());
			mDb.insert(DATABASE_TABLE, null, cv);
		}
	}
	
	public String GetUnusedArticle(int replenish_rate) {
		Cursor mCursor =
		mDb.query(true, DATABASE_TABLE, new String[] { KEY_ROW_ID,
				KEY_ARTICLE_SUMMARY, KEY_USED }, KEY_USED + "= 0", null,
				null, null, null, null);
		int narticles;
		if (mCursor != null) {
			narticles = mCursor.getCount(); 
			if (narticles < LOW_ROWS_THRESHOLD) {
				Log.i(WikiPaper.WP_LOGTAG,
						"Need to replenish article supply...");
				InsertSomeArticles(replenish_rate);
				
				// if we're running on a fresh db, we need to
				// re-run the query to get the articles we just
				// inserted
				if (narticles == 0) {
					mCursor = mDb.query(true, DATABASE_TABLE,
							new String[] {
							KEY_ROW_ID, KEY_ARTICLE_SUMMARY, KEY_USED },
							KEY_USED + "= 0", null, null, null, null, null);
					if (mCursor == null) {
						Log.e(WikiPaper.WP_LOGTAG, "Okay, mCursor is still null... Not good.");
						return null;
					} else {
						narticles = mCursor.getCount();
						if (narticles == 0) {
							Log.e(WikiPaper.WP_LOGTAG, "Okay, db still looks empty... Not good.");
							return null;
						}
					}
				} // eo narticles was 0
			} // eo narticles < LOW_ROWS_THRESHOLD
			
			// retrieve the row and return the summary:
			mCursor.moveToFirst();
			String updateSql = String.format("UPDATE %s SET %s=%s + 1 WHERE %s=%s",
					DATABASE_TABLE, KEY_USED, KEY_USED, KEY_ROW_ID,
					mCursor.getString(mCursor.getColumnIndex(KEY_ROW_ID)));
			mDb.execSQL(updateSql);
			printEntireDb();
			return mCursor.getString(
					mCursor.getColumnIndex(KEY_ARTICLE_SUMMARY));
		}
		return null;
	}
	
	public void printEntireDb() {
		Cursor mCursor = mDb.query(true, DATABASE_TABLE, new String[] {
				KEY_ROW_ID, KEY_ARTICLE_SUMMARY, KEY_USED }, null, null, null,
				null, null, null);
		if (!mCursor.moveToFirst()) {
			Log.e(WikiPaper.WP_LOGTAG, "crap, nothing in the DB to print...");
		}
		do {
			Log.i(WikiPaper.WP_LOGTAG,
					"Here's one: \n"
							+ mCursor.getString(mCursor
									.getColumnIndex(KEY_ROW_ID))
							+ " "
							+ mCursor.getString(mCursor
									.getColumnIndex(KEY_USED))
							+ " "
							+ mCursor.getString(mCursor
									.getColumnIndex(KEY_ARTICLE_SUMMARY)));
		} while (mCursor.moveToNext());
	}

}
