package com.developer.srikanth.speed_test;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import android.os.AsyncTask;

import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;

import android.location.Geocoder;

import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import android.location.Address;

import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;

import com.google.android.gms.common.api.GoogleApiClient;

import android.location.Location;

import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.util.Date;

import java.util.Scanner;
import java.util.Stack;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "MainActivity";

    private GoogleApiClient mGoogleApiClient;

    NotificationCompat.Builder mBuilder;
    NotificationManager mNotifyMgr;

    Geocoder geocoder;
    List<Address> addressList;

    private LocationRequest mLocationRequest;

    private Location mCurrentLocation;
    private Stack<Location> markedLocation;

    private String mLastUpdateTime;

    private String addressTextViewText = "";
    private String prevAddressTextViewText = "";

    private int warningUpdateCount = 0;

    private DownloadManager downloadManager;

    TextView speedTextView, addressTextView, speedLimitTextView;

    private HashMap<String, Integer> mapRoadToSpeedLimit;

    private int currentSpeedLimit = 0;

    private float motionY1, motionY2;
    private static final int MIN_DISTANCE = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //CHECK FOR INTERNET AND LOCATION PERMISSIONS
        ConnectivityManager connectivityManagerCheck = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManagerCheck.getActiveNetworkInfo();
        LocationManager locationManagerCheck = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if ( (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting()) && locationManagerCheck.isProviderEnabled(LocationManager.GPS_PROVIDER) ) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage("Make sure both your GPS and internet settings are turned on.\n\n" +
                            "We use your GPS to determine your speed and we need the internet to determine the speed limit of the road you're on.")
                    .setCancelable(false)
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .setNegativeButton("Continue", null)
                    .show();
            dialog.getButton(dialog.BUTTON_NEGATIVE).setTextColor(Color.RED);
        }

        speedTextView = (TextView) findViewById(R.id.speed_text);

        addressTextView = (TextView) findViewById(R.id.address_text);
        speedLimitTextView = (TextView) findViewById(R.id.speedLimit_text);

        //Create a GoogleApiClient instance
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        //Create a LocationRequest instance
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000) //Request location every second
                .setFastestInterval(500);

        geocoder = new Geocoder(this, Locale.getDefault());

        //REGISTER DOWNLOAD HANDLER
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //PARSE FILE HERE
                long receivedID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1l);
                DownloadManager downloadManagerLocal = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(receivedID);
                Cursor cursor = downloadManagerLocal.query(query);
                int index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                if (cursor.moveToFirst()) {
                    if (cursor.getInt(index) == DownloadManager.STATUS_SUCCESSFUL) {
                        String fileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE));
                        File inputFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Roads/" + fileName);
                        try {

                            currentSpeedLimit = 0;

                            Scanner inputScanner = new Scanner(inputFile);
                            String currentLine = "";
                            while (inputScanner.hasNextLine()) {
                                currentLine = inputScanner.nextLine();
                                if (currentLine.contains("maxspeed")) {
                                    int startIndex = currentLine.indexOf("maxspeed") + 13;
                                    int endIndex = currentLine.indexOf(" mph");
                                    currentSpeedLimit = Integer.parseInt(currentLine.substring(startIndex, endIndex));
                                    break;
                                }
                            }

                            //CASE: SPEED LIMIT NOT FOUND, CHECK FOR ROAD TYPE
                            if (currentSpeedLimit == 0) {
                                Scanner secondInputScanner = new Scanner(inputFile);
                                String secondCurrentLine = "";
                                while (secondInputScanner.hasNextLine()) {
                                    secondCurrentLine = secondInputScanner.nextLine();
                                    if (secondCurrentLine.contains("highway") && secondCurrentLine.contains("residential")) {
                                        currentSpeedLimit = 25; //ESTIMATE
                                        break;
                                    }
                                    //ADD MORE?
                                }
                            }

                            mapRoadToSpeedLimit.put(addressTextViewText, currentSpeedLimit);

                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
                cursor.close();
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        //MANAGE mapRoadToSpeedLimit HASHMAP
        try {
            FileInputStream fileInputStream = new FileInputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/map.bin");
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

            mapRoadToSpeedLimit = (HashMap) objectInputStream.readObject();
            objectInputStream.close();
        } catch (FileNotFoundException e) {
            //MAP IS NOT DEFINED
            mapRoadToSpeedLimit = new HashMap<String, Integer>();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        //CREATE NOTIFICATION
        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("Tracking motion")
                .setContentText("Touch to open.")
                .setOngoing(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(contentIntent);

        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(1, mBuilder.build());
    }

    @Override
    public void onDestroy() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();

        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();

        //MANAGE mapRoadToSpeedLimit HASHMAP
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/map.bin");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(mapRoadToSpeedLimit);
            objectOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_stop) {
            new AlertDialog.Builder(this)
                    .setMessage("You're about to exit. Are you sure?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    MainActivity.this.finish();
                                }
                            })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Location services connected.");

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(!mGoogleApiClient.isConnected())
            mGoogleApiClient.connect();
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "Location services suspended.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, location.toString());

        mCurrentLocation = location;

        float currentSpeed, convertedCurrentSpeed;
        int convertedCurrentSpeedInt;

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        //GET SPEED (or calculate time difference, then use R = D/T)
        if(mCurrentLocation.hasSpeed()) {
            currentSpeed = mCurrentLocation.getSpeed();
            convertedCurrentSpeed = currentSpeed * 3600f * 0.0006213711922f; //"meters per second" to "miles per hour"
            convertedCurrentSpeedInt = (int) convertedCurrentSpeed;
            speedTextView.setText(String.valueOf(convertedCurrentSpeedInt) + " mph");

            //currentSpeedLimit updated from findSpeedLimitAsyncProcess
            new findSpeedLimitAsyncProcess().execute(mCurrentLocation);

            if (currentSpeedLimit != 0 && (convertedCurrentSpeedInt > currentSpeedLimit) ) {
                if (warningUpdateCount < 5) {
                    ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                    warningUpdateCount++;
                }
            } else
                warningUpdateCount = 0;
        } else {
            speedTextView.setText("...");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                motionY1 = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                motionY2 = event.getY();
                float deltaY = motionY2 - motionY1;
                if (Math.abs(deltaY) > MIN_DISTANCE) {
                    if (motionY2 > motionY1)
                        currentSpeedLimit = currentSpeedLimit - 5;
                    else
                        currentSpeedLimit = currentSpeedLimit + 5;
                    mapRoadToSpeedLimit.put(addressTextViewText, currentSpeedLimit);
                }
                break;
        }

        return super.onTouchEvent(event);

    }


    //ASYNC TASK - GET ROAD NAME AND SPEED LIMIT
    private class findSpeedLimitAsyncProcess extends AsyncTask<Location, Void, Integer> {
        //find speed limit based on road

        //String addressTextViewText = "";

        @Override
        protected void onPreExecute() {

            //DELETE FILES IN DIRECTORY EVERY MINUTE?

        }

        @Override
        protected Integer doInBackground(Location... locationParams) {
            Log.i(TAG, "findSpeedLimitAsyncProcess called doInBackground");

            String roadName = "";

            mCurrentLocation = locationParams[0];

            //GET ROAD NAME
            try {
                addressList = geocoder.getFromLocation(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), 1);
                if(addressList.get(0).getAddressLine(0) != null) {
                    roadName = addressList.get(0).getThoroughfare();
                    addressTextViewText = roadName;
                } else {
                    addressTextViewText = "Unknown location";
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (mapRoadToSpeedLimit.containsKey(addressTextViewText) && (mapRoadToSpeedLimit.get(addressTextViewText) != 0) )

                return mapRoadToSpeedLimit.get(addressTextViewText);

            else {

                currentSpeedLimit = 0;

                File fileCheck = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Roads/resource.txt");
                if (fileCheck.exists())
                    fileCheck.delete();

                //GET SPEED LIMIT WITH OSM API CALLS
                String left, bottom, right, top;
                left = String.valueOf(mCurrentLocation.getLongitude() - 0.0005);
                bottom = String.valueOf(mCurrentLocation.getLatitude() - 0.0005);
                right = String.valueOf(mCurrentLocation.getLongitude() + 0.0005);
                top = String.valueOf(mCurrentLocation.getLatitude() + 0.0005);
                String apiCallURL = "http://www.overpass-api.de/api/xapi?way[bbox="
                        + left + ","
                        + bottom + ","
                        + right + ","
                        + top
                        + "]";
                        //+ "[maxspeed=*]";

                downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request downloadRequest = new DownloadManager.Request(Uri.parse(apiCallURL))
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                        .setDestinationInExternalPublicDir("/Roads", "resource.txt");

                downloadManager.enqueue(downloadRequest);

                prevAddressTextViewText = addressTextViewText;
            }

            mapRoadToSpeedLimit.put(addressTextViewText, currentSpeedLimit);
            return currentSpeedLimit;
        }

        @Override
        protected void onPostExecute(Integer speedLimitParam) {
            currentSpeedLimit = speedLimitParam;
            addressTextView.setText(addressTextViewText);
            if (currentSpeedLimit != 0)
                speedLimitTextView.setText(String.valueOf(currentSpeedLimit) + " mph");
            else
                speedLimitTextView.setText("");
        }

    }

}