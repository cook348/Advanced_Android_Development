/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
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
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mTempsTextPaintHigh;
        Paint mTempsTextPaintLow;
        Paint mLinePaint;
        Paint mIconPaint;

        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mTimeXOffset;
        float mTimeYOffset;

        float mDateXOffset;
        float mDateYOffset;

        float mTempsXOffset;
        float mTempsYOffset;

        float mLineXOffset;
        float mLineYOffset;

        float mIconWidth;

        float mIconPadding;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private Bitmap mIconBitmap;

        private SimpleDateFormat mDateFormat;

        // Following the logic of example DigitalWatchFaceService
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        private String mLowTempText = "0";
        private String mHighTempText = "0";


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.time_y_offset);
            mDateYOffset = resources.getDimension(R.dimen.date_y_offset);
            mTempsYOffset = resources.getDimension(R.dimen.temps_y_offset);
            mLineYOffset = resources.getDimension(R.dimen.line_y_offset);

            mIconPadding = resources.getDimension(R.dimen.icon_padding);

//            mIconWidth = resources.getDimension(R.dimen.icon_width);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.lighter_text));
            mTimeTextPaint.setTextAlign(Paint.Align.CENTER);

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.darker_text));
            mDateTextPaint.setTextAlign(Paint.Align.CENTER);

            mTempsTextPaintHigh = new Paint();
            mTempsTextPaintHigh = createTextPaint(resources.getColor(R.color.lighter_text));
            mTempsTextPaintHigh.setTextAlign(Paint.Align.CENTER);

            mTempsTextPaintLow = new Paint();
            mTempsTextPaintLow = createTextPaint(resources.getColor(R.color.darker_text));
//            mTempsTextPaintLow.setTextAlign(Paint.Align.CENTER);

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.lighter_text));

            mIconPaint = new Paint();

            mCalendar = Calendar.getInstance();

            // Load the icon - dummy for now
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.art_clear, null);
            mIconBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            mDateFormat = new SimpleDateFormat("EEE, d MMM, yyyy", Locale.US);


        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Connect GoogleApi Client
                mGoogleApiClient.connect();
                Log.d("WatchFace", "Attempting to connect GoogleApiClient");

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();

                // Disconnect GoogleApi Client Listener
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                    Log.d("WatchFace", "Disconnecting GoogleApiClient");
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            /*
            Time dimensions
             */
            mTimeXOffset = resources.getDimension(isRound
                    ? R.dimen.time_x_offset_round : R.dimen.time_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.time_text_size_round : R.dimen.time_text_size);
            mTimeTextPaint.setTextSize(timeTextSize);

            /*
            Date dimensions
             */
            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            mDateTextPaint.setTextSize(dateTextSize);

            /*
            Temp dimensions
             */
            mTempsXOffset = resources.getDimension(isRound
                    ? R.dimen.temps_x_offset_round : R.dimen.temps_x_offset);
            float tempsTextSize = resources.getDimension(isRound
                    ? R.dimen.temps_text_size_round : R.dimen.temps_text_size);
            mTempsTextPaintHigh.setTextSize(tempsTextSize);
            mTempsTextPaintLow.setTextSize(tempsTextSize);

            // get temp bounds in order to properly size the icon bounding box
            Rect tempBounds = new Rect();
            mTempsTextPaintHigh.getTextBounds("25", 0, "25".length(), tempBounds);

            float textH = tempBounds.bottom - tempBounds.top;
            Log.d("WatchFace", "Text Height = " + Float.toString(textH));

            mIconWidth = textH + mIconPadding; //The height of the text plus padding

            /*
            Line Dimensions
             */
            mLineXOffset = resources.getDimension(R.dimen.line_x_offset);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Get dimensions and center - adapted from https://developer.android.com/training/wearables/watch-faces/drawing.html
            int width = bounds.width();
            int height = bounds.height();

            float centerX = width / 2f;
            float centerY = height / 2f;

            float thirdW = width / 3f;

            float lineHalfLength = 30f;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(text, centerX, mTimeYOffset, mTimeTextPaint);


            if(!mAmbient){

                float tempBaseline = height - mTempsYOffset;

                String date = mDateFormat.format(mCalendar.getTime());

                canvas.drawText(date, centerX, mDateYOffset, mDateTextPaint);

                // TODO use Utility.format temperature to format the temperature received from the device
                canvas.drawText(mHighTempText, centerX, tempBaseline, mTempsTextPaintHigh);
                canvas.drawText(mLowTempText, width - thirdW, tempBaseline, mTempsTextPaintLow);

                // Center Line
                canvas.drawLine(centerX-lineHalfLength, centerY, centerX + lineHalfLength, centerY, mLinePaint);

                // Icon
                RectF rectF = new RectF(thirdW - mIconWidth,
                        tempBaseline-(mIconWidth-mIconPadding/2),
                        thirdW,
                        tempBaseline+mIconPadding/2);

                canvas.drawBitmap(mIconBitmap, null, rectF, null);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
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

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            // TODO instead of getting the asset, get the weatherId and determine which assets to load on this end

            Log.d("Watchface", "onDataChanged Called from inside Engine");
            //TODO handle the data changes here
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if(path.equals("/sunshine-forecast")){
                        double highTemp = dataMap.getDouble("high-temp");
                        double lowTemp = dataMap.getDouble("low-temp");
                        Asset iconAsset = dataMap.getAsset("icon-asset");

                        Log.d("Watchface", "Successfully received data items from mobile");

                        // TODO update the ui elements accordingly
                        mHighTempText = Double.toString(highTemp);
                        mLowTempText = Double.toString(lowTemp);

                        // TODO convert the iconAsset to a bitmap
                        mIconBitmap = loadBitmapFromAsset(iconAsset);
                        invalidate();

                    }
                }

            }
        }

        /**
         * Convenience method from https://developer.android.com/training/wearables/data-layer/assets.html
         * @param asset
         * @return
         */
        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(ConnectionResult.TIMEOUT, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w("WatchFace", "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            if (Log.isLoggable("WatchFace", Log.DEBUG)) {
                Log.d("WatchFace", "GoogleApiClient onConnected: " + bundle);
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            if (Log.isLoggable("WatchFace", Log.DEBUG)) {
                Log.d("WatchFace", "GoogleApiClient onConnectionSuspended: " + i);
            }
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            if (Log.isLoggable("WatchFace", Log.DEBUG)) {
                Log.d("WatchFace", "GoogleApiClient onConnectionFailed: " + connectionResult);
            }
        }
    }
}
