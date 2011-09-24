package com.mgalgs.wikipaper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class WikiPaper extends WallpaperService {
	public static final String WP_LOGTAG= "WikiPaper Log";

	//private static final long DEFAULT_UPDATE_SUMMARY_DELAY_MS = 3600000; // 10 minutes
	private static final long DEFAULT_UPDATE_SUMMARY_DELAY_MS = 20000; // 20 seconds
	
	private final Handler mHandler = new Handler();
	private Article mArticle = new Article();
    private long mLastArticleSwap = -1;
	private TimerTask mUpdateSummaryTextTask;
	private Timer mUpdateSummaryTextTimer = new Timer();
	private Semaphore mArticleAndUpdateTimeMutex = new Semaphore(1);
	private boolean showStats = true;


	private DataManager mDataManager;

	private long mUpdateSummaryTextDelay_ms = DEFAULT_UPDATE_SUMMARY_DELAY_MS;

    @Override
    public void onCreate() {
    	Debug.waitForDebugger();
        Log.i(WP_LOGTAG, "onCreate'ing WikiPaper");
        super.onCreate();
        
        
        mDataManager = new DataManager(this);
        // open data manager and get an article
        mDataManager.open();
		mArticle.summary = getString(R.string.load_text);
		mArticle.title = getString(R.string.load_title);

        mUpdateSummaryTextTask = new TimerTask() {
			@Override
			public void run() {
				DbStats d = mDataManager.getDbStats();
				int howManyToBuffer = 5;
				if (d.nArticles == 0)
					howManyToBuffer = 1;

				Article a = mDataManager.GetUnusedArticle(howManyToBuffer);
				if (a != null) {
					try {
						mArticleAndUpdateTimeMutex.acquire();
					} catch (InterruptedException e) {
						e.printStackTrace();
						Log.e(WP_LOGTAG, "Semaphore interruption???");
						return;
					}
					mArticle = a;
					mLastArticleSwap = SystemClock.elapsedRealtime();
					mArticleAndUpdateTimeMutex.release();
				} else {
					Log.e(WP_LOGTAG, "Error getting more articles in the background...");
				}
			}
		};
		mUpdateSummaryTextTimer.scheduleAtFixedRate(
				mUpdateSummaryTextTask,
				0, // start immediately
				mUpdateSummaryTextDelay_ms);
    } // eo onCreate

    @Override
    public void onDestroy() {
    	Log.i(WP_LOGTAG, "onDestroy'ing WikiPaper");
    	mUpdateSummaryTextTimer.cancel();
        super.onDestroy();
        mDataManager.close();
    }
    
    

    
    @Override
    public Engine onCreateEngine() {
        return new WikiPaperEngine();
    }
    
	class WikiPaperEngine extends Engine {
		
		private static final int DEFAULT_FRAME_RATE = 10; // fps
		private final Paint mSummaryTextPaint = new Paint();
		private final Paint mTitleTextPaint = new Paint();
		private final Paint mStatsTextPaint = new Paint();
		private final Paint mTouchPaint = new Paint();
        private float mTouchX = -1;
        private float mTouchY = -1;
        private boolean mVisible;
		private int mFrameRate = DEFAULT_FRAME_RATE; // fps
		
		@SuppressWarnings("unused")
		private long mStartTime;
		@SuppressWarnings("unused")
		private float mOffset;
		@SuppressWarnings("unused")
		private float mCenterX;
		@SuppressWarnings("unused")
		private float mCenterY;
		@SuppressWarnings("unused")
		private int mWidth;
		@SuppressWarnings("unused")
		private int mHeight;
		

		private final Runnable mDrawPaper= new Runnable() {
			public void run() {
				drawFrame();
			}
		};
		private final int mTextOffset_x = 10;
		private final int mTextOffset_y = 60;
		private int mTextPadding_sides = 20;
		private int mTextPadding_topbottom = 20;
		private float mSummaryTextFontSize = 30;
		private float mTitleFontSize = 60;
		private float mStatsTextFontSize = 15;

		public WikiPaperEngine() {
			// Create a Paint to draw the text
			final Paint tpaint = mTouchPaint;
			tpaint.setColor(0xffffffff);
			tpaint.setAntiAlias(true);
			tpaint.setStrokeWidth(2);
			tpaint.setStrokeCap(Paint.Cap.ROUND);
			tpaint.setStyle(Paint.Style.STROKE);
			
			mSummaryTextPaint.setColor(0xffffffff);
			mSummaryTextPaint.setAntiAlias(true);
			mSummaryTextPaint.setStrokeWidth(1);
			mSummaryTextPaint.setStrokeCap(Paint.Cap.ROUND);
			mSummaryTextPaint.setStyle(Paint.Style.STROKE);
			mSummaryTextPaint.setTypeface(Typeface.SERIF);
			mSummaryTextPaint.setTextSize(mSummaryTextFontSize );
			
			mTitleTextPaint.setColor(0xaaffffff);
			mTitleTextPaint.setAntiAlias(true);
			mTitleTextPaint.setStrokeWidth(2);
			mTitleTextPaint.setStrokeCap(Paint.Cap.SQUARE);
			mTitleTextPaint.setStyle(Paint.Style.STROKE);
			mTitleTextPaint.setTypeface(Typeface.SERIF);
			mTitleTextPaint.setTextSize(mTitleFontSize);

			mStatsTextPaint.setColor(0xaaffffff);
			mStatsTextPaint.setAntiAlias(true);
			mStatsTextPaint.setStrokeWidth(1);
			mStatsTextPaint.setStrokeCap(Paint.Cap.ROUND);
			mStatsTextPaint.setStyle(Paint.Style.STROKE);
			mStatsTextPaint.setTypeface(Typeface.SERIF);
			mStatsTextPaint.setTextSize(mStatsTextFontSize );
			
			mStartTime = SystemClock.elapsedRealtime();
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

		/*
		 * Store the position of the touch event so we can use it for drawing
		 * later
		 */
		@Override
		public void onTouchEvent(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				mTouchX = event.getX();
				mTouchY = event.getY();
			} else {
				mTouchX = -1;
				mTouchY = -1;
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
            //c.save();
            //c.translate(mCenterX, mCenterY);
            c.drawColor(0xff000000);
            
            try {
				mArticleAndUpdateTimeMutex.acquire();
			} catch (InterruptedException e) {
				Log.e(WP_LOGTAG, "Interrupted exception?? Wow!!!");
				e.printStackTrace();
				return;
			}
            String summary = mArticle.summary;
            String title = mArticle.title;
            mArticleAndUpdateTimeMutex.release();

            int textHeight = c.getHeight() - mTextOffset_y - mTextPadding_topbottom ;
            int textWidth = c.getWidth() - mTextOffset_x - mTextPadding_sides;
            int y = mTextOffset_y;
            y = drawSomeText(title, c, mTitleTextPaint, textWidth, textHeight,
            		mTextOffset_x, y);
            y = drawSomeText(summary, c, mSummaryTextPaint, textWidth, textHeight,
            		mTextOffset_x, y + (mTextPadding_topbottom / 2));
            
            if (showStats) {
            	DbStats d = mDataManager.getDbStats();
            	String txt;
            	if (d != null) {
					txt = String
							.format("Cache stats: %d unused articles, %d total articles",
									d.nUnusedArticles, d.nArticles);
					y = drawSomeText(txt, c, mStatsTextPaint, textWidth,
							textHeight, mTextOffset_x, y
									+ (mTextPadding_topbottom / 2));
            	}
				if (mLastArticleSwap != -1) {
					int s_til_refresh = (int) ((mLastArticleSwap
							+ mUpdateSummaryTextDelay_ms
							- SystemClock.elapsedRealtime())/1000);
					if (s_til_refresh < 0) {
						txt = "Refreshing...";
					} else {
						txt = String.format("%d seconds until next refresh",
								s_til_refresh);
					}
					y = drawSomeText(txt, c, mStatsTextPaint, textWidth,
							textHeight, mTextOffset_x, y
									+ (mTextPadding_topbottom / 2));

				}
            }

            //c.restore();
        }

        // returns the last y location we painted at
        int drawSomeText(String txt, Canvas c, Paint p, int textWidth, int textHeight,
        		int startX, int startY) {
			String line = "";
			List<String> words = Arrays.asList(txt.split(" "));
			List<String> lines = new ArrayList<String>();
			// TODO: replace this with breakText
			for (String word : words) {
				String newword = " " + word;
				if (p.measureText(line + newword) > textWidth) {
					lines.add(line);
					line = "";
				}
				line += newword;
			}
			lines.add(line); // add the last line

			// fix up the initial y offset:
			Rect b = new Rect();
			p.getTextBounds("A", 0, 1, b);

			int y_txt = startY + b.height();
			int x_txt = startX;
			for (String thisLine : lines) {
				c.drawText(thisLine, x_txt, y_txt, p);

				Rect bounds = new Rect();
				p.getTextBounds(thisLine, 0, thisLine.length(), bounds);
				y_txt += bounds.height();
			}
			return y_txt;
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
