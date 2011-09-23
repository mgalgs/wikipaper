package com.mgalgs.wikipaper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;

import android.R.bool;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
	private String mSummaryText;
	private TimerTask mUpdateSummaryTextTask;
	private Timer mUpdateSummaryTextTimer = new Timer();
	private Semaphore mSummaryTextMutex = new Semaphore(1);


	private DataManager mDataManager;

	private long mUpdateSummaryTextDelay_ms = DEFAULT_UPDATE_SUMMARY_DELAY_MS;

    @Override
    public void onCreate() {
        super.onCreate();
        
        mDataManager = new DataManager(this);
        // open data manager and get an article
        mSummaryText = mDataManager.open().GetUnusedArticle(1);
        if (mSummaryText == null)
        	mSummaryText = getString(R.string.load_text);
        
        mUpdateSummaryTextTask = new TimerTask() {
			@Override
			public void run() {
				String txt = mDataManager.GetUnusedArticle(5);
				if (txt != null) {
					try {
						mSummaryTextMutex.acquire();
					} catch (InterruptedException e) {
						e.printStackTrace();
						Log.e(WP_LOGTAG, "Semaphore interruption???");
						return;
					}
					mSummaryText = txt;
					mSummaryTextMutex.release();
				} else {
					Log.e(WP_LOGTAG, "Error getting more articles in the background...");
				}
			}
		};
		mUpdateSummaryTextTimer.scheduleAtFixedRate(
				mUpdateSummaryTextTask,
				mUpdateSummaryTextDelay_ms ,
				mUpdateSummaryTextDelay_ms);
    }

    @Override
    public void onDestroy() {
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
		private final Paint mTextPaint = new Paint();
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
		private int mTextOffset_x = 10;
		private int mTextOffset_y = 80;

		public WikiPaperEngine() {
			// Create a Paint to draw the text
			final Paint tpaint = mTouchPaint;
			tpaint.setColor(0xffffffff);
			tpaint.setAntiAlias(true);
			tpaint.setStrokeWidth(2);
			tpaint.setStrokeCap(Paint.Cap.ROUND);
			tpaint.setStyle(Paint.Style.STROKE);
			
			final Paint txtpaint = mTextPaint;
			txtpaint.setColor(0xffffffff);
			txtpaint.setAntiAlias(true);
			txtpaint.setStrokeWidth(2);
			txtpaint.setStrokeCap(Paint.Cap.ROUND);
			txtpaint.setStyle(Paint.Style.STROKE);
			

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
            
            int cwidth = c.getWidth();
            

            String line = "";
            List<String> words = Arrays.asList(mSummaryText.split(" "));
            List<String> lines = new ArrayList<String>();
            for (String word : words) {
            	String newword = " " + word;
            	if (mTextPaint.measureText(line + newword) > cwidth) {
            		lines.add(line);
            		line = "";
            	}
            	line += newword;
            }
            lines.add(line); // add the last line
            
            int x_txt = mTextOffset_x;
            int y_txt = mTextOffset_y;
            for (String thisLine : lines) {
            	c.drawText(thisLine, x_txt, y_txt, mTextPaint);
            	
            	Rect bounds = new Rect();
				mTextPaint.getTextBounds(thisLine, 0, thisLine.length(), bounds);
				y_txt += bounds.height();
            }
            

            //c.restore();
        }



        /*
         * Draw a circle around the current touch point, if any.
         */
        void drawTouchPoint(Canvas c) {
            if (mTouchX >=0 && mTouchY >= 0) {
                c.drawCircle(mTouchX, mTouchY, 80, mTextPaint);
            }
        }


	} //eo class WikiPaperEngine
	
	
	
	
	
	
	
	
	
	
//    /** Called when the activity is first created. */
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main);
//        
//        mSummaryText = (TextView) findViewById(R.id.article_text);
//        
//        ((Button) findViewById(R.id.button1)).setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				mSummaryText.setText("Fetching article...");
//				String summary = WikiParse.getRandomArticleSummary();
//				if (summary == null) {
//					mSummaryText.setText("Error getting summary");
//				} else {
//					mSummaryText.setText(summary);
//					Log.d(WP_LOGTAG, summary);
//				}
//			}
//		});
//    }
}