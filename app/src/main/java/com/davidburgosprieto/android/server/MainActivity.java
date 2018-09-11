package com.davidburgosprieto.android.server;

import android.os.Bundle;
import android.app.Activity;
import android.widget.TextView;

public class MainActivity extends Activity {
    Server mServer;
    TextView mInfoIpTextView, mMessageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mInfoIpTextView = findViewById(R.id.infoip);
        mMessageTextView = findViewById(R.id.msg);
        mServer = new Server(this);
        mInfoIpTextView.setText(mServer.getIpAddress() + ":" + mServer.getPort());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mServer.onDestroy();
    }
}