package com.mgalgs.wikipaper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class WikiPaper extends WallpaperService {
	public static final String WP_LOGTAG= "WikiPaper Log";

	//private static final long DEFAULT_SWAP_ARTICLE_DELAY_MS = 20000; // 20 seconds
	private static final long DEFAULT_SWAP_ARTICLE_DELAY_MS = 60000; // 1 minute

	private static final int MAX_ARTICLE_BUFFERING_AT_A_TIME = 4;

	private static final long DEFAULT_CACHE_UPDATE_DELAY_MS = 5000; // 5 seconds

	private static final long DEFAULT_ARTICLE_SCROLL_PERIOD_MS = 30000; // 30 seconds
	private static final int DEFAULT_FRAME_RATE = 20; // fps

	public static final String SHARED_PREFS_NAME = "WikiPaperSettings";

	public static final int MAX_TOUCH_DY = 50;
	
	private Article mArticle = new Article();
    private long mLastArticleSwap = -1;
	private boolean showStats = true;

	private final ScheduledExecutorService mExecutorService = Executors
			.newScheduledThreadPool(2);

	private DataManager mDataManager;

	private long mSwapDisplayedArticleDelay_ms = DEFAULT_SWAP_ARTICLE_DELAY_MS;
	private long mCacheUpdateDelay_ms = DEFAULT_CACHE_UPDATE_DELAY_MS;
	private long mArticleScrollPeriod_ms = DEFAULT_ARTICLE_SCROLL_PERIOD_MS;
	private int mFrameRate = DEFAULT_FRAME_RATE; // fps

	private CacheUpdater mCurrentlyRunningCacheUpdater;

	private int mBufferingCnt = 1;

	
	private final Paint mSummaryTextPaint = new Paint();
	private final Paint mStatsTextPaint = new Paint();
	private final Paint mTitleTextPaint = new Paint();
	private Paint mBackgroundPaint = new Paint();
	private float mSummaryTextFontSize = 30;
	private float mStatsTextFontSize = 15;
	private float mTitleFontSize = 60;
	private int mWidth;
	private int mHeight;
	private int mTextPadding_sides = 20;
	private int mTextPadding_topbottom = 20;
	private int mStatsBottomOffset = 100;
	private int mTitleOffset = 60;
	private Picture mSummaryPicture = new Picture();
	private Picture mTitlePicture = new Picture();
	private DbStats mDbStats = null;
	private Object mDbStatsLock = new Object();

	private int mSummaryHeight = -1;
	private int mTitleHeight;
	private int mStatsHeight;
	private int mHeightOfAnM_Stats;
	private Integer mLastYoffset = 0;
	private int mScrollSpeed = 3;

	private int mTextColor = 0xaaffffff;
	private int mBackgroundColor = 0xff000000;
	
	private boolean mWifiOnly = true;
	
	private SharedPreferences mPrefs;
	private Object preferencesLock = new Object();


	private ArticleSwapper mCurrentlyRunningSwapper;

	
	public WikiPaper() {
		initPaints();
	}
	
	private void initPaints() {
		mSummaryTextPaint.setColor(mTextColor);
		mSummaryTextPaint.setAntiAlias(true);
		mSummaryTextPaint.setStrokeWidth(1);
		mSummaryTextPaint.setStrokeCap(Paint.Cap.ROUND);
		mSummaryTextPaint.setStyle(Paint.Style.FILL);
		mSummaryTextPaint.setTypeface(Typeface.SERIF);
		mSummaryTextPaint.setTextSize(mSummaryTextFontSize);

		mStatsTextPaint.setColor(mTextColor);
		mStatsTextPaint.setAntiAlias(true);
		mStatsTextPaint.setStrokeWidth(1);
		mStatsTextPaint.setStrokeCap(Paint.Cap.ROUND);
		mStatsTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		mStatsTextPaint.setTypeface(Typeface.SANS_SERIF);
		mStatsTextPaint.setTextSize(mStatsTextFontSize);
		Rect b = new Rect();
		mStatsTextPaint.getTextBounds("M", 0, 1, b);
		mHeightOfAnM_Stats = b.height();
		mStatsHeight = (int) ((mHeightOfAnM_Stats * 1.5)) * 4; // 4 lines in stats

		mTitleTextPaint.setColor(mTextColor);
		mTitleTextPaint.setAntiAlias(true);
		mTitleTextPaint.setStrokeWidth(2);
		mTitleTextPaint.setStrokeCap(Paint.Cap.SQUARE);
		mTitleTextPaint.setStyle(Paint.Style.FILL);
		mTitleTextPaint.setTypeface(Typeface.SERIF);
		mTitleTextPaint.setTextSize(mTitleFontSize);

		mBackgroundPaint.setColor(mBackgroundColor);
	}


    @Override
    public void onCreate() {
//    	Log.i(WP_LOGTAG, "waiting for debugger");
//    	Debug.waitForDebugger();
        super.onCreate();
        Log.i(WP_LOGTAG, "onCreate'ing WikiPaper");
        
        // open data manager and get an article
        mDataManager = new DataManager(this);
        mDataManager.open();
		mArticle.summary = getString(R.string.load_text);
		mArticle.title = getString(R.string.load_title);

		// preferences:
		mPrefs = WikiPaper.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
		mSwapDisplayedArticleDelay_ms = Integer.parseInt(mPrefs.getString(
				"wp_article_refresh_rate", "60")) * 1000;
		mWifiOnly = mPrefs.getBoolean("wp_wifi_dl_only", true);
		mFrameRate = Integer.parseInt(mPrefs.getString("wp_frame_rate", "25"));
		mDataManager.mLowRowsThreshold = Integer.parseInt(mPrefs.getString(
				"wp_fresh_articles", "20"));
		mDataManager.mMaxArticles = Integer.parseInt(mPrefs.getString(
				"wp_max_articles", "500"));
		mArticleScrollPeriod_ms = Math.min(mSwapDisplayedArticleDelay_ms,
				mArticleScrollPeriod_ms);
		mScrollSpeed = Integer.parseInt(mPrefs
				.getString("wp_scroll_speed", "3"));

		scheduleCacheUpdate(100,
				false, // one-time update
				true // force ui update
				);
		scheduleCacheUpdate(10000,
				true, // forever updating
				false //no ui updates
				);
		// start the article swapping:
		mCurrentlyRunningSwapper = new ArticleSwapper();
		mExecutorService.schedule(mCurrentlyRunningSwapper,
				mSwapDisplayedArticleDelay_ms, TimeUnit.MILLISECONDS);
    } // eo onCreate

    private void scheduleCacheUpdate(long delay_ms, boolean repeating, boolean doUiUpdate) {
    	if (repeating && mBufferingCnt  < MAX_ARTICLE_BUFFERING_AT_A_TIME)
    		mBufferingCnt *= 2;

    	mCurrentlyRunningCacheUpdater = new CacheUpdater(mBufferingCnt, repeating, doUiUpdate);
		mExecutorService.schedule(mCurrentlyRunningCacheUpdater, delay_ms,
				TimeUnit.MILLISECONDS);
    }

    public class CacheUpdater implements Runnable {
    	private int mHowManyToBuffer;
    	private boolean mRepeating;
    	private boolean mDoUiUpdate;

    	CacheUpdater(int howManyToBuffer, boolean repeating, boolean doUiUpdate) {
    		mHowManyToBuffer = howManyToBuffer;
    		mRepeating= repeating;
    		mDoUiUpdate = doUiUpdate;
    	}

		@Override
		public synchronized void run() {
			ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			Boolean wifiOnly;
			synchronized (preferencesLock) {
				wifiOnly = mWifiOnly;
			}
			if (!wifiOnly
					|| connManager
							.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
							.isConnected()) {
				mDataManager.maybeReplenishDb(mHowManyToBuffer);
			}
			if (mDoUiUpdate)
				doArticleSwap();
			if (mRepeating)
				scheduleCacheUpdate(mCacheUpdateDelay_ms, mRepeating, mDoUiUpdate);
		}
    }
    
    public class ArticleSwapper implements Runnable {
    	private boolean keepGoing = true;
    	@Override
    	public synchronized void run() {
    		if (!keepGoing) return;
    		doArticleSwap();
    		mCurrentlyRunningSwapper = new ArticleSwapper();
			mExecutorService.schedule(mCurrentlyRunningSwapper,
					mSwapDisplayedArticleDelay_ms, TimeUnit.MILLISECONDS);
    	}
    }
    
    public void doArticleSwap() {
		synchronized (mArticle) {
			try {
				Article a = mDataManager.GetUnusedArticle();
				if (a != null) {
					mArticle = a;
					mLastArticleSwap = SystemClock.elapsedRealtime();
					onArticleRefresh();
				} else {
					Log.e(WP_LOGTAG, "Looks like we're out of articles...");
				}
			} catch (Exception e) {
				Log.e(WP_LOGTAG, "Wow!!! Exception doing article update");
				e.printStackTrace();
			}
		}
	}

	public void onArticleRefresh() {
		synchronized (mSummaryPicture) {
			Canvas c = mSummaryPicture.beginRecording(mWidth, mHeight);
			String theSummary = mArticle.summary.length() > 4 ? mArticle.summary
					: mArticle.title;
			mSummaryHeight = drawSomeText(theSummary, c, mSummaryTextPaint,
					mWidth - mTextPadding_sides, mHeight,
					mTextPadding_topbottom / 2, mTextPadding_topbottom / 2);
			mSummaryPicture.endRecording();
		}

		synchronized (mTitlePicture) {
			Canvas c = mTitlePicture.beginRecording(mWidth, mHeight);
			mTitleHeight = drawSomeText(mArticle.title, c, mTitleTextPaint,
					mWidth - mTextPadding_sides, mHeight,
					mTextPadding_topbottom / 2, mTitleOffset);
			mTitlePicture.endRecording();
		}

		synchronized (mDbStatsLock) {
			mDbStats = mDataManager.getDbStats();
		}
		
		synchronized (mLastYoffset) {
			mLastYoffset = 0;
		}
	}
    
	// returns the last y location we painted at
	int drawSomeText(String txt, Canvas c, Paint p, int textWidth,
			int textHeight, int startX, int startY) {
		String line = "";
		List<String> words = Arrays.asList(txt.split(" "));
		List<String> lines = new ArrayList<String>();
		// TODO: replace this with breakText
		for (String word : words) {
			if (word.endsWith("\n")) {
				word = word.replace('\n', ' ');
				line += " " + word;
				lines.add(line);
				line = "";
			} else if (p.measureText(line + " " + word) > textWidth) {
				lines.add(line);
				line = word;
			} else {
				line += " " + word;
			}
		}
		lines.add(line); // add the last line

		// fix up the initial y offset:
		Rect b = new Rect();
		p.getTextBounds("M", 0, 1, b);
		int heightOfAnM = (int) (b.height() * 1.5);

		int y_txt = startY + heightOfAnM;
		int x_txt = startX;
		int totalHeight = 0;
		for (String thisLine : lines) {
			c.drawText(thisLine, x_txt, y_txt, p);
			y_txt += heightOfAnM;
			totalHeight += heightOfAnM;
		}
		return totalHeight;
	}



    @Override
    public void onDestroy() {
    	Log.i(WP_LOGTAG, "onDestroy'ing WikiPaper");
    	mExecutorService.shutdown();
        super.onDestroy();
        mDataManager.close();
    }
    
    

    
    @Override
    public Engine onCreateEngine() {
        return new WikiPaperEngine();
    }
    
	class WikiPaperEngine extends Engine implements
			SharedPreferences.OnSharedPreferenceChangeListener {
	
		private final Handler mHandler = new Handler();

		private final Paint mTouchPaint = new Paint();
        private float mTouchX = -1;
        private float mTouchY = -1;
        private boolean mVisible;
        

		
		@SuppressWarnings("unused")
		private long mStartTime;
		@SuppressWarnings("unused")
		private float mOffset;
		@SuppressWarnings("unused")
		private float mCenterX;
		@SuppressWarnings("unused")
		private float mCenterY;
		

		private final Runnable mDrawPaper= new Runnable() {
			public void run() {
				drawFrame();
			}
		};

		private boolean firstDraw = true;

		private Integer mForcedDY = -1;

		public WikiPaperEngine() {
			// Create a Paint to draw the text
			final Paint tpaint = mTouchPaint;
			tpaint.setColor(0xffffffff);
			tpaint.setAntiAlias(true);
			tpaint.setStrokeWidth(2);
			tpaint.setStrokeCap(Paint.Cap.ROUND);
			tpaint.setStyle(Paint.Style.STROKE);
			
			mStartTime = SystemClock.elapsedRealtime();
			
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(mPrefs, null);
		}

		public void onSharedPreferenceChanged(SharedPreferences prefs,
				String key) {
			synchronized (preferencesLock ) {
				mWifiOnly = prefs.getBoolean("wp_wifi_dl_only", true);
				mFrameRate = Integer.parseInt(prefs.getString("wp_frame_rate",
						"25"));
				mDataManager.mLowRowsThreshold = Integer.parseInt(prefs
						.getString("wp_fresh_articles", "20"));
				mDataManager.mMaxArticles = Integer.parseInt(prefs
						.getString("wp_max_articles", "500"));
				int newSwapDelay = Integer.parseInt(prefs.getString(
						"wp_article_refresh_rate", "60")) * 1000;
				if (newSwapDelay != mSwapDisplayedArticleDelay_ms) {
					mSwapDisplayedArticleDelay_ms = newSwapDelay;

					mCurrentlyRunningSwapper.keepGoing = false;
					// does the old reference die off if I just replace it like so:
					mCurrentlyRunningSwapper = new ArticleSwapper();
					mExecutorService.schedule(mCurrentlyRunningSwapper,
							mSwapDisplayedArticleDelay_ms,
							TimeUnit.MILLISECONDS);

					mArticleScrollPeriod_ms = Math.min(
							mSwapDisplayedArticleDelay_ms,
							mArticleScrollPeriod_ms);
				}
				mScrollSpeed  = Integer.parseInt(prefs.getString("wp_scroll_speed",
						"3"));

			}
		}


		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);

			// By default we don't get touch events, so enable them.
			setTouchEventsEnabled(true);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			mHandler.removeCallbacks(mDrawPaper);
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			mVisible = visible;
			if (visible) {
				drawFrame();
			} else {
				mHandler.removeCallbacks(mDrawPaper);
			}
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
			super.onSurfaceChanged(holder, format, width, height);
			// store the center of the surface, so we can draw the cube in the
			// right spot
			mCenterX = width / 2.0f;
			mCenterY = height / 2.0f;
			mWidth = width;
			mHeight = height;
			if (firstDraw ) {
				onArticleRefresh();
				firstDraw = false;
			}
			drawFrame();
		}

		@Override
		public void onSurfaceCreated(SurfaceHolder holder) {
			super.onSurfaceCreated(holder);
		}

		@Override
		public void onSurfaceDestroyed(SurfaceHolder holder) {
			super.onSurfaceDestroyed(holder);
			mVisible = false;
			mHandler.removeCallbacks(mDrawPaper);
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset, float xStep,
				float yStep, int xPixels, int yPixels) {
			mOffset = xOffset;
			drawFrame();
		}

		private float mLastTouchY = -1;
		/*
		 * Store the position of the touch event so we can use it for drawing
		 * later
		 */
		@Override
		public void onTouchEvent(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				mTouchX = event.getX();
				mTouchY = event.getY();
				synchronized(mForcedDY) {
					if (mForcedDY == -1) {
						mLastTouchY = mTouchY;
					}
					int dy = (int) (mLastTouchY - mTouchY);
					dy *= 2; // speed things up a bit
					if (dy > 0) {
						mForcedDY = Math.min(dy, MAX_TOUCH_DY);
					} else {
						mForcedDY = Math.max(dy, -MAX_TOUCH_DY);
					}
					
				}
				mLastTouchY = mTouchY;
			} else {
				mTouchX = -1;
				mTouchY = -1;
				synchronized(mForcedDY) {
					mForcedDY = -1;
				}
			}
			super.onTouchEvent(event);
		}
		
		
		/*
		 * Draw one frame of the animation. This method gets called repeatedly
		 * by posting a delayed Runnable. You can do any drawing you want in
		 * here. This example draws a wireframe cube.
		 */
		void drawFrame() {
			final SurfaceHolder holder = getSurfaceHolder();

			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null) {
					// draw something
					drawPaper(c);
					drawTouchPoint(c);
				}
			} catch (Exception e) {
				Log.e(WP_LOGTAG, "Wow, exception while drawing frame!");
				e.printStackTrace();
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}

			// Reschedule the next redraw
			mHandler.removeCallbacks(mDrawPaper);
			if (mVisible) {
				mHandler.postDelayed(mDrawPaper, 1000 / mFrameRate);
			}
		}
		
		
        void drawPaper(Canvas c) {
            c.drawColor(0xff000000);

            int titleHeight, summaryHeight;
            synchronized (mTitlePicture) {
            	titleHeight = mTitleHeight;
			}
            synchronized (mSummaryPicture) {
            	summaryHeight = mSummaryHeight;
            }
            int statsHeight = mStatsHeight;

            int stats_y = mHeight - statsHeight - mStatsBottomOffset;

            long ms_since_refresh = SystemClock.elapsedRealtime() - mLastArticleSwap; 

//            int y = (int)lerp((float)ms_since_refresh % mArticleScrollPeriod_ms,
//            				0F, (float)mArticleScrollPeriod_ms ,
//            				(float)stats_y, (float)(titleHeight - summaryHeight));
            int lastYoffset;
            synchronized(mLastYoffset) {
            	lastYoffset = mLastYoffset;
            	lastYoffset %= ((stats_y - titleHeight) + summaryHeight);
            }
            int y = stats_y - lastYoffset;
            int dy;
            synchronized(mForcedDY ) {
            	if (mForcedDY != -1) {
            		dy = mForcedDY;
            		mForcedDY = -1;
            	} else {
            		dy = mScrollSpeed;
            	}
            }
            lastYoffset += dy;
            synchronized(mLastYoffset) {
            	mLastYoffset = lastYoffset;
            }
            
            // In order to have the summary text scroll up "underneath"
            // the title text, we have to lay down a black rectangle and
            // then draw the stuffs in this order:
            // 1) Summary
            // 2) black rectangle
            // 3) Title
            // 4) Stats (order doesn't matter since it should never overlap)
            
            // draw the summary (translated down)
            c.save();
            c.translate(0, y);
            synchronized(mSummaryPicture) {
            	c.drawPicture(mSummaryPicture);
            }
            c.restore();
            
            // draw a rectangle for the title background:
			c.drawRect(0, 0, mWidth, titleHeight + mTitleOffset
					+ mTextPadding_topbottom, mBackgroundPaint);

            // draw the title
			synchronized(mTitlePicture) {
				c.drawPicture(mTitlePicture);
			}
            
            // draw the stats
            c.drawRect(0, stats_y, mWidth, mHeight, mBackgroundPaint);
            
			if (showStats && summaryHeight != -1) {
				String txt;
				synchronized(mDbStatsLock) {
					if (mDbStats != null) {
						txt = String.format("Cache stats:\n"
								+ "   %d unused article%s, %d total article%s",
								mDbStats.numUnusedArticles,
								mDbStats.numUnusedArticles == 1 ? "" : "s",
								mDbStats.numArticles,
								mDbStats.numArticles == 1 ? "" : "s");
						stats_y += drawSomeText(txt, c, mStatsTextPaint,
								mWidth, mHeight, mTextPadding_topbottom / 2,
								stats_y + (mTextPadding_topbottom / 2));
					}
				}
				synchronized (mArticle) {
					if (mLastArticleSwap != -1) {
						long ms_til_refresh = mSwapDisplayedArticleDelay_ms
								- ms_since_refresh;
						int s_til_refresh = (int) ms_til_refresh / 1000;
						if (s_til_refresh < 0) {
							txt = "Refreshing...";
						} else {
							txt = String.format(
									"%d second%s until next refresh",
									s_til_refresh, s_til_refresh == 1 ? ""
											: "s");
						}
						stats_y += mHeightOfAnM_Stats; // "carriage return"
						stats_y += drawSomeText(txt, c, mStatsTextPaint,
								mWidth, mHeight, mTextPadding_topbottom / 2,
								stats_y + (mTextPadding_topbottom / 2));
					}
				}
			}
        }

        
        // returns the linear interpolation of the value given the two ranges
        // For example, if you have a number in the
        // range [0..500] that you'd like to map to a number in the range
        // [0..255] you would call this function like so:
        // newVal = edm.lerp(num, 0,500, 0,255)
        public float lerp(float val, float x0, float x1, float y0, float y1) {
        	// equation taken from http://en.wikipedia.org/wiki/Linear_interpolation
        	return y0 + (val - x0)*((y1-y0)/(x1-x0));
        }


        /*
         * Draw a circle around the current touch point, if any.
         */
        void drawTouchPoint(Canvas c) {
            if (mTouchX >=0 && mTouchY >= 0) {
                c.drawCircle(mTouchX, mTouchY, 80, mSummaryTextPaint);
            }
        }

	} //eo class WikiPaperEngine
}
