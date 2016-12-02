package com.example.android.sunshine.app;

import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class WeatherDataService extends WearableListenerService {

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d("Watchface", "onDataChanged Called from inside service");

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

//                    // TODO update the ui elements accordingly
//                    mHighTempText = Double.toString(highTemp);
//                    mLowTempText = Double.toString(lowTemp);
//
//                    // TODO convert the iconAsset to a bitmap
//                    mIconBitmap = loadBitmapFromAsset(iconAsset);
//                    invalidate();

                }
            }

        }
    }
}
