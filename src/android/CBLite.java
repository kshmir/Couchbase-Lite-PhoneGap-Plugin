package com.couchbase.cblite.phonegap;

import android.content.Context;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;

import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.auth.BasicAuthenticator;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Database;
import com.couchbase.lite.listener.LiteListener;
import com.couchbase.lite.listener.LiteServlet;
import com.couchbase.lite.listener.Credentials;
import com.couchbase.lite.router.URLStreamHandlerFactory;
import com.couchbase.lite.View;
import com.couchbase.lite.javascript.JavaScriptViewCompiler;
import com.couchbase.lite.util.Log;

import java.util.List;
import java.util.ArrayList;

import java.net.MalformedURLException;
import java.net.URL;

import java.io.IOException;
import java.io.File;

public class CBLite extends CordovaPlugin implements Replication.ChangeListener {

    public static String TAG = "SyncGateway";

    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final String DATABASE_NAME = "contacts";
    public static final String SYNC_URL = "http://45.79.199.113:4988/contacts";
    private boolean initFailed = false;
    private int listenPort;
    private Credentials allowedCredentials;
    private boolean inicializado = false;
    private Manager manager = null;
    private Database database;

    /**
     * Constructor.
     */
    public CBLite() {
        super();
        System.out.println("CBLite() constructor called");
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        System.out.println("initialize() called");

        super.initialize(cordova, webView);
        initCBLite();

    }

    private void initCBLite() {
        try {
            URLStreamHandlerFactory.registerSelfIgnoreError();

            View.setCompiler(new JavaScriptViewCompiler());

            this.manager = startCBLite(this.cordova.getActivity());

            this.database = this.manager.getDatabase(DATABASE_NAME);

            listenPort = startCBLListener(DEFAULT_LISTEN_PORT, this.manager, null);

            inicializado = true;

            System.out.println("initCBLite() completed successfully");


        } catch (final Exception e) {
            e.printStackTrace();
            initFailed = true;
        }

    }

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callback) {
        if (action.equals("startSync")) {
            try {

                String user = args.getString(0);
                String password = args.getString(1);
                String url = null;
                if (args.length() > 2) {
                    url = args.getString(2);
                } else {
                    url = null;
                }

                if (manager != null) {
                    startSync(user, password, url);
                }

            } catch (final Exception e) {
                e.printStackTrace();
                callback.error(e.getMessage());
            }
        }
        return false;
    }

    protected Manager startCBLite(Context context) {
        Manager manager;
        try {
            Manager.enableLogging(Log.TAG, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_SYNC, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_CHANGE_TRACKER, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_BLOB_STORE, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_LISTENER, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_MULTI_STREAM_WRITER, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_REMOTE_REQUEST, Log.VERBOSE);
            Manager.enableLogging(Log.TAG_ROUTER, Log.VERBOSE);
            manager = new Manager(new AndroidContext(context), Manager.DEFAULT_OPTIONS);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return manager;
    }

    private void startSync(String user, String password, String optionalUrl) {

        URL syncUrl;
        try {
            if (optionalUrl != null) {
                syncUrl = new URL(optionalUrl);
            } else {

                syncUrl = new URL(SYNC_URL);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        Replication pullReplication = this.database.createPullReplication(syncUrl);
        pullReplication.setContinuous(true);

        Replication pushReplication = this.database.createPushReplication(syncUrl);
        pushReplication.setContinuous(true);

        BasicAuthenticator authenticator = new BasicAuthenticator(user, password);
        pullReplication.setAuthenticator(authenticator);
        pushReplication.setAuthenticator(authenticator);

        pullReplication.addChangeListener(this);
        pushReplication.addChangeListener(this);

        pullReplication.start();
        pushReplication.start();

    }

    @Override
    public void changed(Replication.ChangeEvent event) {

        Replication replication = event.getSource();
        Log.d(TAG, "Replication : " + replication + " changed.");
        if (!replication.isRunning()) {
            String msg = String.format("Replicator %s not running", replication);
            Log.d(TAG, msg);
        } else {
            int processed = replication.getCompletedChangesCount();
            int total = replication.getChangesCount();
            String msg = String.format("Replicator processed %d / %d", processed, total);
            Log.d(TAG, msg);
        }

      /*if (event.getError() != null) {
        showError("Sync error", event.getError());
      }*/

    }

    private int startCBLListener(int listenPort, Manager manager, Credentials allowedCredentials) {
        LiteListener listener = new LiteListener(manager, listenPort, allowedCredentials);
        int boundPort = listener.getListenPort();
        Thread thread = new Thread(listener);
        thread.start();
        return boundPort;

    }

    public void onResume(boolean multitasking) {
        System.out.println("CBLite.onResume() called");
    }

    public void onPause(boolean multitasking) {
        System.out.println("CBLite.onPause() called");
    }

}
