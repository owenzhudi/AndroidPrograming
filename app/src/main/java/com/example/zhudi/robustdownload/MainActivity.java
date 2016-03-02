package com.example.zhudi.robustdownload;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private EditText mUrlText;
    private Button mDownloadButton;
    private Button mLogButton;
    private String mDownloadUrl;
    private Context mContext;
    private DownloadManager mDownloadManager;
    private CheckBox mCheckAnyWifi;
    private CheckBox mCheckRuWifi;
    private WifiNetwork mWifi;
    private DownloadReceiver mDownloadReceiver;
    //private String mWifiName;
    //private DownloadTask mDownloadTask;
    private String mFileName = "log.txt";
    private MyTime startTime;
    private long startTimeInMs;
    private long endTimeInMs;
    private int duration; // in seconds
    private LogFile mLog;
    private ProgressBar mDownloadProgress;
    private TextView progressText;
    private TextView mLogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "test");

        mDownloadButton = (Button) findViewById(R.id.download_button);
        mLogButton = (Button) findViewById(R.id.log_button);
        mUrlText = (EditText) findViewById(R.id.url_text);
        mDownloadUrl = mUrlText.getText().toString();
        //mDownloadUrl = "http://www.winlab.rutgers.edu/~janne/mobisys14gesturesecurity.pdf";
        mCheckAnyWifi = (CheckBox) findViewById(R.id.any_wifi);
        mCheckRuWifi = (CheckBox) findViewById(R.id.ru_wifi);
        mContext = this;
        mWifi = new WifiNetwork();
        mDownloadReceiver = new DownloadReceiver();
        //mWifiName = "";
        //mDownloadTask = new DownloadTask();
        startTime = new MyTime();
        mLog = new LogFile(mFileName);
        mDownloadProgress = (ProgressBar)findViewById(R.id.download_progress);
        mDownloadProgress.setVisibility(View.INVISIBLE);
        mDownloadProgress.setMax(100);
        progressText = (TextView)findViewById(R.id.show_progress);


        // register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(mDownloadReceiver, filter);

        // display text of log file
        mLogText = (TextView) findViewById(R.id.log_text);

        // watch the edittext change
        mUrlText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                mDownloadUrl = mUrlText.getText().toString();
            }
        });

        if(mWifi.isWifiConnected()) {
            startTimeInMs = System.currentTimeMillis();
            startTime = new MyTime();
        }

        // automatically download if in RU WiFi network
        if(mCheckRuWifi.isChecked() && mWifi.isRuWifiNetwork() && mWifi.isWifiConnected()) {
            new DownloadTask().execute();
            Toast.makeText(MainActivity.this, "Automatically download under RU WiFi...", Toast.LENGTH_SHORT).show();
        }

        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCheckAnyWifi.isChecked()) {
                    if(mWifi.isWifiConnected()) {
                        new DownloadTask().execute();
                    } else {
                        Toast.makeText(MainActivity.this, "WiFi is not connected!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if(!mWifi.isRuWifiNetwork()) {
                        new DownloadTask().execute();
                    }
                }
                if(mCheckRuWifi.isChecked() && !mWifi.isRuWifiNetwork()) {
                    Toast.makeText(MainActivity.this, "You do not connect RU WiFi!", Toast.LENGTH_SHORT).show();
                }
            }

        });

        mLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mLog.readLog();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private class DownloadReceiver extends BroadcastReceiver {
        private String logText = "";
        @Override
        public void onReceive(Context context, Intent intent) {
            long reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            // Network change
            if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                mWifi = new WifiNetwork();
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    endTimeInMs = System.currentTimeMillis();
                    duration = (int)(endTimeInMs - startTimeInMs) / 1000;
                    Toast.makeText(MainActivity.this, "Wifi is disconnected", Toast.LENGTH_SHORT).show();
                    // write log file
                    logText = "Connection time:" + startTime.toString() + "\n" + mWifi.getWifiName()
                            + " "+ duration + "s\n";
                    try {
                        mLog.writeLog(logText);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                } else if(info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    // automatically download if wifi connected
                    startTimeInMs = System.currentTimeMillis();
                    startTime = new MyTime();
                    new DownloadTask().execute();

                }
            }
        }
    }

    private class WifiNetwork {
        private WifiManager wifiManager;
        private int wifiState;
        private WifiInfo info;
        private String name;

        public WifiNetwork() {
            wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            info = wifiManager.getConnectionInfo();
            name = (info != null) ? info.getSSID() : null;
            wifiState = wifiManager.getWifiState();
        }

        public boolean isRuWifiNetwork() {
            return (name.equals("RUWireless") || name.equals("RUWireless_Secure")
                    || name.equals("LAWN") || name.equals("ECE"));
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isWifiConnected() {
            return (wifiState == wifiManager.WIFI_STATE_ENABLED);
        }

        public String getWifiName() {
            return name;
        }
    }

    private class DownloadTask extends AsyncTask<Void, Integer, Void> {

        private long startTime;
        private long receiveTime;
        private long endTime;
        private int fileSize;
        private int downloadedBytes;
        private long downloadId;

        @Override
        protected Void doInBackground(Void... params) {

            mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mDownloadUrl));
            //request.setDescription("File is being downloaded...");

            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String nameOfFile = URLUtil.guessFileName(mDownloadUrl, null,
                    MimeTypeMap.getFileExtensionFromUrl(mDownloadUrl));
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nameOfFile);

            downloadId = mDownloadManager.enqueue(request);
            receiveTime = System.currentTimeMillis();

            // calculate and show percentage of download progress
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor c = mDownloadManager.query(query);
            Log.d(TAG, "download id: " + downloadId);

            do {
                if (c.moveToFirst()) {
                    int fileSizeIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int downloadedBytesIndex = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    fileSize = c.getInt(fileSizeIndex);
                    // There is a problem of getting the downloaded file size, so I manually set the file size for measurement
                    //fileSize = 740402;
                    c.moveToFirst();
                    downloadedBytes = c.getInt(downloadedBytesIndex);
                    //downloadedBytes += 100000;
                    Integer percent = (int) ((double) downloadedBytes / fileSize * 100);
                    Log.d(TAG, "percent= " + percent);
                    publishProgress(percent);
                    if(percent >= 100) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "fileSizeIndex = " + fileSizeIndex);
                    Log.d(TAG, "downloadedBytesIndex = " + downloadedBytesIndex);
                }

                Log.d(TAG, "fileSize=" + fileSize);
                Log.d(TAG, "receive time " + receiveTime);
                Log.d(TAG,"downloaded bytes: "+downloadedBytes);
            } while(downloadedBytes < fileSize);


            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mDownloadProgress.setVisibility(View.VISIBLE);
            startTime = System.currentTimeMillis();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            mDownloadProgress.setProgress(progress[0]);
            Log.d(TAG,progress[0]+"");
            progressText.setText(progress[0] + "%");

        }

        @Override
        protected void onPostExecute(Void param) {
            endTime = System.currentTimeMillis();
            Toast.makeText(MainActivity.this, "Download complete", Toast.LENGTH_SHORT).show();
            mDownloadProgress.setVisibility(View.INVISIBLE);
            Log.d(TAG, "end time" + endTime);
            record();
        }

        private long latency() {
            return receiveTime - startTime;
        }

        private long speed() {
            return (long) (fileSize / ((double)(endTime - receiveTime)/1000));
        }

        public void record() {
            String text = "latency: " + latency() + "ms\n" +
                    "throughput: " + speed() + "bytes/s \n";
            try {
                mLog.writeLog(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class MyTime {
        private Date mDate;
        private SimpleDateFormat mDateFormat;

        public MyTime() {
            mDate = new Date();
        }
        public Date getCurrentTime() {
            mDate = new Date();
            return mDate;
        }

        public String toString() {
            mDateFormat = new SimpleDateFormat("MM/dd/yyyy h:m:s a");
            return mDateFormat.format(mDate);
        }
    }
    private class LogFile {
        private FileInputStream in;
        private FileOutputStream out;
        private String textToWrite;
        private String fileName;

        public LogFile(String fileName) {
            this.fileName = fileName;
        }

        public void writeLog(String text) throws IOException{
            try {
                out = mContext.openFileOutput(fileName, Context.MODE_APPEND);
                textToWrite = text;
                byte[] bytes = textToWrite.getBytes();
                out.write(bytes);
                out.flush();
                out.close();
            } catch(IOException e) {
                e.printStackTrace();
            }

        }

        public void readLog() throws IOException{

            try {
                in = openFileInput(fileName);
                int length = in.available();
                byte[] buffer = new byte[length];
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                while(in.read(buffer) != -1) {
                    byteArrayOutputStream.write(buffer, 0, buffer.length);
                }
                in.close();
                byteArrayOutputStream.close();
                String res = new String(byteArrayOutputStream.toByteArray());
                //mLayout.addView(mLogText);
                mLogText.setText(res);
            } catch(IOException e) {
                e.printStackTrace();
            }

        }

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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mDownloadReceiver);
    }
}
