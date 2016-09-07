package com.example.android.sunshine.app;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * 1. Connects to GoogleApi.
 * 2. Sends data through the API so it is shared with all the wearables.
 * 3. Closes the connection to the Google api.
 * <p/>
 * This is done when it receives an intent from the data-fetching(-from-the-internet) service.
 */
public class MyWatchService extends IntentService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final String ACTION_UPDATE_WEAR_DATA = "ACTION_UPDATE_WEAR_DATA";
    private static final String LOG_TAG = MyWatchService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
    };


    public MyWatchService() {
        super("MyWatchService");
    }


    public MyWatchService(String name) {
        super(name);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "in onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(LOG_TAG, "in onStart");
        super.onStart(intent, startId);
        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_UPDATE_WEAR_DATA)) {
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext(), this, this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG_TAG, "in onHandleIntent");
        if (intent != null && intent.getAction().equals(ACTION_UPDATE_WEAR_DATA)) {
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext(), this, this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(LOG_TAG, "in onConnected");
        // We need a specific endpoint/path for this request.
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/wear");

        Context context = getBaseContext();

        String locationQuery = Utility.getPreferredLocation(context);



        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
        Cursor cursor = context.getContentResolver().query(weatherUri, FORECAST_COLUMNS, null, null, null);

        if (cursor.moveToFirst()) {
            double tempMax = cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP));
            double tempMin = cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP));
            int weatherID = cursor.getInt(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));

            String strTempMax = Utility.formatTemperature(getBaseContext(), tempMax);
            String strTempMin = Utility.formatTemperature(getBaseContext(), tempMin);

            Log.d(LOG_TAG, "RETRIEVED DATA: " + strTempMax + " - " + strTempMin + " - " + weatherID);

            // We send already formatted temperature strings
            putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis()); // to make sure onDataChanged is called during testing.
            putDataMapRequest.getDataMap().putString("KEY_TEMP_MAX", strTempMax);
            putDataMapRequest.getDataMap().putString("KEY_TEMP_MIN", strTempMin);
            putDataMapRequest.getDataMap().putInt("KEY_WEATHER_ID", weatherID);

            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).setResultCallback(
                    new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                            Log.d(LOG_TAG, "in onResult...");
                            if (dataItemResult.getStatus().isSuccess()) {
                                Log.d(LOG_TAG, "in onResult, GREAT URGENT SUCCESS");
                            }
                        }
                    }
            );
        }
        cursor.close();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
