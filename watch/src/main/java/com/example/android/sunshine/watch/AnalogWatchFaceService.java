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

package com.example.android.sunshine.watch;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class AnalogWatchFaceService extends CanvasWatchFaceService {

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {
        private final String LOG_TAG = Engine.class.getSimpleName();
        private Bitmap mBackgroundBitmap;
        private Bitmap mWeatherIconBitmap;
        private Paint mBackgroundPaint;
        boolean mAmbient;

        private Calendar mCalendar;
        private SimpleDateFormat mTimeFormat;
        private java.text.DateFormat mDateFormat;
        private Date mDate;

        // dimens
        private float mYOffset;
        private float mLineHeight;
        private float mDelimiterWidth;

        public Paint mSecondaryPaint;
        public Paint mTempMinPaint;
        public Paint mTempMaxPaint;
        public Paint mCurrentDayPaint;
        public Paint mHourPaint;


        // Sensible defaults so we can use them to decide the font size for the temperature.
        private String mTempMax = "20°";
        private String mTempMin = "20°";
        private int mWeatherIconResource = R.drawable.ic_clear;
        private float mCurrentDayDesiredWidth;

        private GoogleApiClient mGoogleApiClient;


        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AnalogWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();

            Resources resources = AnalogWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.y_offset);
            mLineHeight = resources.getDimension(R.dimen.line_height);
            mDelimiterWidth = resources.getDimension(R.dimen.delimiter_width);

            mBackgroundPaint = new Paint();
            int primaryTextColor = resources.getColor(R.color.text_primary);
            int secondaryTextColor = resources.getColor(R.color.text_secondary);
            mBackgroundPaint.setColor(resources.getColor(R.color.primary)); //mInteractiveBackgroundColor);
            mSecondaryPaint = createTextPaint(secondaryTextColor);//mSecondaryColor);
            mCurrentDayPaint = createTextPaint(secondaryTextColor);//resources.getColor(R.color.text_secondary));//mSecondaryColor);

            mHourPaint = createTextPaint(primaryTextColor);

            mTempMaxPaint = createTextPaint(primaryTextColor, BOLD_TYPEFACE);
            mTempMinPaint = createTextPaint(secondaryTextColor);

            // Dynamic text size to fit a desired width.
            long now = System.currentTimeMillis();
            mDate.setTime(now);
            mCurrentDayDesiredWidth = getResources().getDimension(R.dimen.current_day_width);

            AnalogWatchFaceUtils.setTextSizeForWidth(mCurrentDayPaint,
                    mCurrentDayDesiredWidth,
                    getCurrentDayString(mDate).toUpperCase());

            AnalogWatchFaceUtils.setTextSizeForWidth(mHourPaint,
                    getResources().getDimension(R.dimen.current_time_width),
                    getCurrentTimeString(mDate).toUpperCase());

            int desiredTempWidth = (int) (resources.getDimension(R.dimen.current_day_width) / 4);
            Log.e(LOG_TAG, String.format("Temp width desired: %d", desiredTempWidth));

            AnalogWatchFaceUtils.setTextSizeForWidth(mTempMaxPaint,
                    desiredTempWidth,
                    resources.getString(R.string.temperature_example));

            AnalogWatchFaceUtils.setTextSizeForWidth(mTempMinPaint,
                    desiredTempWidth,
                    resources.getString(R.string.temperature_example));

            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg);
            mWeatherIconBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_clear);


            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext(), this, this)
                    .addApi(Wearable.API)
                    .build();

            Log.d(LOG_TAG, "in Engine.onCreate");
            mGoogleApiClient.connect();
        }


        private void initFormats() {
            mTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            mDateFormat = new SimpleDateFormat("EE, MMM dd yyyy", Locale.getDefault());

            mTimeFormat.setCalendar(mCalendar);
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
            /* the time changed */
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.d(LOG_TAG, "in onAmbientModeChanged");
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;
            /* the wearable switched between modes. We want to re-draw*/
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            final int mStartX = canvas.getWidth() / 2;
            final int mStartY = canvas.getHeight() / 3;
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawPaint(mBackgroundPaint);
            }

            canvas.drawText(getCurrentTimeString(mDate).toUpperCase(), mStartX, mStartY, mHourPaint);

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            mCurrentDayPaint.setTextAlign(Paint.Align.CENTER);
            mHourPaint.setTextAlign(Paint.Align.CENTER);
            mTempMinPaint.setTextAlign(Paint.Align.CENTER);
            mTempMaxPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(getCurrentDayString(mDate).toUpperCase(), mStartX, mStartY + mYOffset,
                    mCurrentDayPaint);

            // Delimiter
            canvas.drawLine(mStartX - mDelimiterWidth / 2,
                    mStartY + (int) (1.5 * mYOffset),
                    mStartX + mDelimiterWidth / 2,
                    mStartY + (int) (1.5 * mYOffset),
                    mSecondaryPaint);

            int weatherIconY = mStartY + (int) (2 * mYOffset);
            if (!mAmbient) {
                // We dont want to display the weather icon in ambient mode.
                // Align icon with the start of current day string.
                canvas.drawBitmap(mWeatherIconBitmap, mStartX - mCurrentDayDesiredWidth / 2,
                        weatherIconY,
                        null);
            }
            int temperatureY = (int) (weatherIconY + mYOffset);

            // We want the temp max/min to be evenly distributed on the remaining width.
            int postIconX = (int) (mStartX - (mCurrentDayDesiredWidth / 2 - mWeatherIconBitmap.getScaledWidth(canvas)));
            int remainingWidth = (int) (mCurrentDayDesiredWidth - mWeatherIconBitmap.getScaledWidth(canvas));

            AnalogWatchFaceUtils.setTextSizeForWidth(mTempMaxPaint, 2 * remainingWidth / 5, mTempMax);
            AnalogWatchFaceUtils.setTextSizeForWidth(mTempMinPaint, 2 * remainingWidth / 5, mTempMin);

            Log.d(LOG_TAG, String.format("Icon scaled width: %d", mWeatherIconBitmap.getScaledWidth(canvas)));
            Log.d(LOG_TAG, String.format("Canvas width: %d", canvas.getWidth()));
            Log.d(LOG_TAG, String.format("temperatureY: %d", temperatureY));
            float tempMaxCenterX = postIconX + remainingWidth / 4;

            canvas.drawText(mTempMax,
                    tempMaxCenterX,
                    temperatureY,
                    mTempMaxPaint);
            canvas.drawText(mTempMin,
                    postIconX + 3 * remainingWidth / 4,
                    temperatureY,
                    mTempMinPaint);
        }

        private String getCurrentTimeString(Date date) {
            return mTimeFormat.format(date);
        }

        private String getCurrentDayString(Date date) {
            return mDateFormat.format(date);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            // This is where we'd get our custom resources for round/square watches.
            // (Since there are no resource qualifiers as for phone size/density, we need to do it manually)
            // boolean isRound = insets.isRound();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */
            if (visible) {
                if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
                    mGoogleApiClient.connect();
                }

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        }


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "in onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, this);

            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(
                    new ResultCallback<DataItemBuffer>() {
                        @Override
                        public void onResult(@NonNull DataItemBuffer dataItems) {
                            Log.d(LOG_TAG, "in onResult!");
                            Log.d(LOG_TAG, String.format("in onResult, %d items...", dataItems.getCount()));
                            for (DataItem dataItem : dataItems) {
                                parseData(dataItem);
                            }
                            dataItems.release();
                        }
                    }
            );
        }

        @Override
        public void onConnectionSuspended(int i) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(LOG_TAG, "in onConnectionFailed");
            Log.e(LOG_TAG, String.valueOf(connectionResult.getErrorCode()));
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                // 2 types of event: TYPE_CHANGED, TYPE_DELETED. That's it.
                Log.d(LOG_TAG, String.format("DATA EVENT TYPE: %s", dataEvent.getType()));
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    parseData(dataEvent.getDataItem());
                }
                dataEventBuffer.release();
            }
        }

        private void parseData(DataItem dataItem) {
            Uri uri = dataItem.getUri();
            Log.d(LOG_TAG, String.format("in parseData, event from uri: %s", uri.toString()));

            DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
            mTempMax = dataMap.getString("KEY_TEMP_MAX");
            mTempMin = dataMap.getString("KEY_TEMP_MIN");
            mWeatherIconResource = AnalogWatchFaceUtils.getIconResourceForWeatherCondition(dataMap.getInt("KEY_WEATHER_ID"));
            mWeatherIconBitmap = BitmapFactory.decodeResource(getResources(), mWeatherIconResource);
            invalidate();
        }
    }
}
