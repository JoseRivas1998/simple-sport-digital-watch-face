package com.example.simplesportdigitalwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;
        public static final float TOP_SHAPE_HEIGHT = 0.17f;
        public static final float TOP_SHAPE_CENTER_PERCENT = 0.165f;
        public static final float DAY_OF_WEEK_X_PERCENT = 0.325f;
        public static final float CENTER_SHAPE_HEIGHT_PERCENT = 0.18f;
        public static final float CENTER_SHAPE_CENTER_PERCENT = 0.37f;
        public static final float DATE_TIME_X_PERCENT = 0.175f;
        public static final float BOTTOM_SHAPE_HEIGHT_PERCENT = 0.16f;
        public static final float BOTTOM_SHAPE_CENTER_PERCENT = 0.57f;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private int mWatchWidth;
        private int mWatchHeight;

        private Paint backgroundPatternPaint;

        private Paint backgroundShapesPaint;
        private float[][] backgroundShapes;

        private TextPaint textPaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            backgroundPatternPaint = new Paint();
            backgroundPatternPaint.setColor(getResources().getColor(R.color.background_lines, getTheme()));

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);

            final int[] backgroundShapeIDs = {
                    R.array.day_of_week_shape,
                    R.array.time_shape,
                    R.array.date_shape
            };

            backgroundShapesPaint = new Paint();
            backgroundShapesPaint.setColor(getResources().getColor(R.color.background_shapes, getTheme()));

            backgroundShapes = new float[backgroundShapeIDs.length][];
            for(int i = 0; i < backgroundShapeIDs.length; i++) {
                backgroundShapes[i] = pointArrayToFloatArray(backgroundShapeIDs[i]);
            }
        }

        private void initializeWatchFace() {

            textPaint = new TextPaint();
            textPaint.setColor(getResources().getColor(R.color.text_color, getTheme()));
            textPaint.setTypeface(Typeface.createFromAsset(getAssets(), "sevenseg.ttf"));

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if(mAmbient) {
                textPaint.setColor(getResources().getColor(R.color.ambient_text_color, getTheme()));
            } else {
                textPaint.setColor(getResources().getColor(R.color.text_color, getTheme()));
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);


            mWatchWidth = width;
            mWatchHeight = height;
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(getResources().getColor(R.color.background, getTheme()));
                drawBackgroundLines(canvas);
                drawBackgroundShapes(canvas);
            }

        }

        private void drawBackgroundLines(Canvas canvas) {
            final float SPACING = 15;
            for (float startY = 0; startY < mWatchHeight; startY += SPACING) {
                canvas.drawLine(0, startY, mWatchWidth, startY + SPACING, backgroundPatternPaint);
            }
        }

        private void drawPolygon(Canvas canvas, float[] points, Paint paint) {
            if (points.length < 4) {
                return;
            }
            if (points.length % 2 != 0) {
                return;
            }
            Path p = new Path();
            p.moveTo(points[0] * mWatchWidth, points[1] * mWatchHeight);
            for (int i = 2; i < points.length; i += 2) {
                p.lineTo(points[i] * mWatchWidth, points[i + 1] * mWatchHeight);
            }
            p.lineTo(points[0] * mWatchWidth, points[1] * mWatchHeight);
            canvas.drawPath(p, paint);
        }

        private float[] pointArrayToFloatArray(final int arrayID) {
            TypedArray typedArray = getResources().obtainTypedArray(arrayID);
            float[] res = new float[typedArray.length()];
            for(int i = 0; i < typedArray.length(); i++) {
                res[i] = typedArray.getFloat(i, 0);
            }
            typedArray.recycle();
            return res;
        }


        private void drawBackgroundShapes(Canvas canvas) {
            for (float[] backgroundShape : backgroundShapes) {
                drawPolygon(canvas, backgroundShape, backgroundShapesPaint);
            }
        }

        private void drawWatchFace(Canvas canvas) {
            drawDayOfWeek(canvas);
            drawTime(canvas);
            drawDate(canvas);
        }

        private void drawDate(Canvas canvas) {
            final int month = mCalendar.get(Calendar.MONTH);
            final int day = mCalendar.get(Calendar.DAY_OF_MONTH);
            final int year = mCalendar.get(Calendar.YEAR);
            final String dateString = String.format("%d-%d-%d", month, day, year);
            textPaint.setTextSize(mWatchHeight * BOTTOM_SHAPE_HEIGHT_PERCENT);
            final float dateHeight = getTextHeight(dateString, textPaint);
            final float dateY = (BOTTOM_SHAPE_CENTER_PERCENT * mWatchHeight) + (dateHeight * 0.5f);
            final float dateX = DATE_TIME_X_PERCENT * mWatchWidth;
            canvas.drawText(dateString, dateX, dateY, textPaint);
        }

        private void drawTime(Canvas canvas) {
            final int hour = mCalendar.get(Calendar.HOUR) == 0 ? 12 : mCalendar.get(Calendar.HOUR);
            final int minute = mCalendar.get(Calendar.MINUTE);
            final String timeString;
            if(mAmbient) {
                timeString = String.format("%d:%02d", hour, minute);
            } else {
                final int second = mCalendar.get(Calendar.SECOND);
                timeString = String.format("%d:%02d:%02d", hour, minute, second);
            }
            textPaint.setTextSize(mWatchHeight * CENTER_SHAPE_HEIGHT_PERCENT);
            final float timeHeight = getTextHeight(timeString, textPaint);
            final float timeY = (CENTER_SHAPE_CENTER_PERCENT * mWatchHeight) + (timeHeight * 0.5f);
            final float timeX = DATE_TIME_X_PERCENT * mWatchWidth;
            canvas.drawText(timeString, timeX, timeY, textPaint);
        }

        private void drawDayOfWeek(Canvas canvas) {
            String dayOfWeek = getDayOfWeek(mCalendar);
            textPaint.setTextSize(mWatchHeight * TOP_SHAPE_HEIGHT);
            final float dayOfWeekHeight = getTextHeight(dayOfWeek, textPaint);
            final float dayOfWeekY = (TOP_SHAPE_CENTER_PERCENT * mWatchHeight) + (dayOfWeekHeight * 0.5f);
            final float dayOfWeekX = DAY_OF_WEEK_X_PERCENT * mWatchWidth;
            canvas.drawText(dayOfWeek, dayOfWeekX, dayOfWeekY, textPaint);
        }

        private float getTextHeight(String text, TextPaint textPaint) {
            final Rect rect = new Rect();
            textPaint.getTextBounds(text, 0, text.length(), rect);
            return rect.height();
        }

        private String getDayOfWeek(Calendar calendar) {
            String weekDay;
            switch (calendar.get(Calendar.DAY_OF_WEEK)) {
                case Calendar.SUNDAY:
                    weekDay = "SUN";
                    break;
                case Calendar.MONDAY:
                    weekDay = "MON";
                    break;
                case Calendar.TUESDAY:
                    weekDay = "TUE";
                    break;
                case Calendar.WEDNESDAY:
                    weekDay = "WED";
                    break;
                case Calendar.THURSDAY:
                    weekDay = "THU";
                    break;
                case Calendar.FRIDAY:
                    weekDay = "FRI";
                    break;
                case Calendar.SATURDAY:
                    weekDay = "SAT";
                    break;
                default:
                    weekDay = "";
            }
            return weekDay;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
