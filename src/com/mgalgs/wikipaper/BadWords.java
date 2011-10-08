package com.mgalgs.wikipaper;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.content.Context;
import android.util.Log;

public final class BadWords {
	private static final String ASSET_BADWORDS = "badwords.txt";
	private ArrayList<String> mBadWordsList = new ArrayList<String>();

	BadWords(Context ctx) {
		readBadWordsList(ctx);
	}
	
	private void readBadWordsList(Context ctx) {
		mBadWordsList.clear();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(ctx.getAssets()
					.open(ASSET_BADWORDS)));
			String line;
			while ((line = in.readLine()) != null)
				mBadWordsList.add(line);
		} catch (IOException e) {
			Log.e(WikiPaper.WP_LOGTAG, "Error while reading badwords file!!!");
			e.printStackTrace();
		} finally {
			closeStream(in);
		}
	}
	
	private static void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				Log.e(WikiPaper.WP_LOGTAG, "What the... Error closing stream.");
				e.printStackTrace();
			}
		}
	}
	
	boolean hasBadWord(String txt) {
		if (mBadWordsList == null) {
			Log.i(WikiPaper.WP_LOGTAG, "The bad words list hasn't been initialized. Assuming not NSFW.");
			return false;
		}
		for (String badword : mBadWordsList) {
			try {
				if (txt.matches("\\W" + badword + "\\W")) {
					Log.i(WikiPaper.WP_LOGTAG, "Uh-oh, that text has the word "
							+ badword + " in it.");
					return true;
				}

			} catch (Exception e) {
				Log.e(WikiPaper.WP_LOGTAG, "Error while doing a regex match...");
				e.printStackTrace();
			}
		}
		return false;
	}
}
