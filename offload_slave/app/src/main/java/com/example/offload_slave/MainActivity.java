package com.example.offload_slave;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = "Offload Slave";

    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    private String masterEndpointId;
    private Button findMasterButton;

    private Button disconnectButton;
    private ProgressBar progressBar;
    private TextView progressMessage;

    private Intent batteryStatus;
    private ProgressBar batteryProgress;
    private TextView batteryLevel;
    private TextView latitude;
    private TextView longitude;
    private String masterName;
    private TextView  masterText;
    private TextView status;
    private Timer timer;
    private int requestCode;

    private static int requestCounter = 0;

    private ConnectionsClient connectionsClient;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        findMasterButton = findViewById(R.id.find_opponent);
        disconnectButton = findViewById(R.id.disconnect);
        progressBar = (ProgressBar) findViewById(R.id.progressBar3);
        progressBar.setVisibility(View.GONE);
        progressMessage = (TextView) findViewById(R.id.progressMessage);
        progressMessage.setVisibility(View.GONE);
        batteryProgress = (ProgressBar) findViewById(R.id.progressBar6);
        batteryLevel = (TextView) findViewById(R.id.batteryLevel);
        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);
        latitude.setVisibility(View.GONE);
        longitude.setVisibility(View.GONE);
        connectionsClient = Nearby.getConnectionsClient(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setUiLocation();
        masterText = findViewById(R.id.opponent_name);
        status = findViewById(R.id.status);
        TextView nameView = findViewById(R.id.name);
        nameView.setText(getString(R.string.slaveName, "Slave device"));
        TimerTask timerTask = new updateBatteryLevel();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 10 * 1000);
        startDiscovery();
        resetUI();
        findMasterButton.setVisibility(View.GONE);
    }

    public class BatteryLocationTimer extends TimerTask {
        @Override
        public void run() {
            sendBatteryLocationData(100);
        }

    }

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    try {
                        final InputStream is = payload.asStream().asInputStream();
                        ByteSource byteSource = new ByteSource() {
                            @Override
                            public InputStream openStream() throws IOException {
                                return is;
                            }
                        };

                        JSONObject jsonObject = null;

                        try {
                            String data = byteSource.asCharSource(Charsets.UTF_8).readFirstLine();
                            Log.d("Payload : ", data);
                            jsonObject = new JSONObject(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (jsonObject.has("request_code")) {
                            requestCode = jsonObject.getInt("request_code");
                        }

                        switch (requestCode) {
                            case 100:
                                // Request to monitor battery and location

                                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                                alertDialogBuilder.setMessage("Master send request to start monitoring. Allow him ?");
                                alertDialogBuilder.setPositiveButton("yes",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface arg0, int arg1) {
                                                TimerTask timerTask = new BatteryLocationTimer();
                                                //running timer task as daemon thread
                                                timer = new Timer(true);
                                                timer.scheduleAtFixedRate(timerTask, 0, 20 * 1000);
                                            }
                                        });

                                alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        JSONObject obj = new JSONObject();
                                        try {
                                            obj.put("request_code", requestCode);
                                            obj.put("request_status", 500);
                                            obj.put("device_name", "slave device");
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                        Log.println(Log.INFO, "Request  : ", obj.toString());
                                        connectionsClient.sendPayload(
                                                masterEndpointId, Payload.fromStream(new ByteArrayInputStream(obj.toString().getBytes(UTF_8))));
                                        finish();
                                    }
                                });

                                AlertDialog alertDialog = alertDialogBuilder.create();
                                alertDialog.show();


                                break;
                            case 101:
                                //Request for Matrix Multiplication
                                requestCounter++;
                                Gson gson = new Gson();
                                progressMessage.setText("No. of Request Served : " + requestCounter);
                                progressMessage.setVisibility(View.VISIBLE);
                                progressBar.setVisibility(View.VISIBLE);
                                int A[][] = gson.fromJson(jsonObject.getString("A"), int[][].class);
                                int B[][] = gson.fromJson(jsonObject.getString("B"), int[][].class);
                                //Log.println(Log.INFO, "Matrix Number",""+( matrix[0][0]+1));
                                //Make JSON object
                                JSONObject matrixResponse = new JSONObject();
                                String C ="matrix impl pending"; //gson.toJson(getMatrixMutliplication(A, B));
                                Log.println(Log.INFO, "Matrix C", "" + C);
                                try {
                                    matrixResponse.put("request_code", requestCode);
                                    matrixResponse.put("request_status", 200);
                                    matrixResponse.put("device_name", "slave device");
                                    matrixResponse.put("C", C);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                connectionsClient.sendPayload(
                                        masterEndpointId, Payload.fromStream(new ByteArrayInputStream(matrixResponse.toString().getBytes(UTF_8))));
                                break;
                            default:
                                ;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                }
            };


    private void resetUI() {
        masterEndpointId = null;
        masterName = null;
        setStatusText(getString(R.string.STATUS_DISCONNECTED));
        setMasterName(getString(R.string.no_master));
        setButtonState(false);
    }


    private void setMasterName(String masterName) {
        masterText.setText(getString(R.string.master_name, masterName));
    }
    void setUiLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            latitude.setText("Latitude : " + location.getLatitude());
                            longitude.setText("Longitude : " + location.getLongitude());
                            longitude.setVisibility(View.VISIBLE);
                            latitude.setVisibility(View.VISIBLE);

                        }
                    }

                }).addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("failure", e.getMessage());
            }
        }).addOnCanceledListener(this, new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Log.d("Cancelled", "Cancelled");
            }
        }).addOnCompleteListener(this, new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {

                Log.d("Completed", "completed");
            }
        });

    }

    public class updateBatteryLevel extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    double batteryStatus = getBatteryStatus();
                    batteryProgress = (ProgressBar) findViewById(R.id.progressBar6);
                    batteryProgress.setProgress((int) batteryStatus);
                    batteryLevel.setText(batteryStatus + "%");

                }
            });
        }
}
    private double getBatteryStatus() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = this.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return level * 100 / (double) scale;
    }

    private void startDiscovery() {
        connectionsClient.startDiscovery(
                getPackageName(), endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    // Callbacks for finding other devices
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "Endpoint found: connecting");
                    connectionsClient.requestConnection("slave", endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {
                }
            };

    // Callbacks for connections to other devices
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(final String endpointId, final ConnectionInfo connectionInfo) {

                    Log.i(TAG, "onConnectionInitiated: accepting connection");

                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertDialogBuilder.setMessage("Are you sure,You wanted to accept the request ?");
                    alertDialogBuilder.setPositiveButton("yes",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface arg0, int arg1) {
                                    Toast.makeText(MainActivity.this, "You clicked yes button", Toast.LENGTH_LONG).show();
                                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                                    masterName = connectionInfo.getEndpointName();
                                }
                            });

                    alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();


                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful");
                        sendBatteryLocationData(100);
                        masterEndpointId = endpointId;
                        setMasterName(masterName);
                        setStatusText(getString(R.string.STATUS_CONNECTED));
                        setButtonState(true);

                    } else {
                        Log.i(TAG, "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                    findMasterButton.setVisibility(View.VISIBLE);
                    progressMessage.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    connectionsClient.stopDiscovery();
                    requestCounter = 0;
                    resetUI();
                    if (timer != null)
                        timer.cancel();
                }
            };
    private void setButtonState(boolean connected) {
        findMasterButton.setEnabled(true);
        findMasterButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);
    }


    public void disconnect(View view) {
        connectionsClient.disconnectFromEndpoint(masterEndpointId);
        resetUI();
    }
    public void findMaster(View view) {

        startDiscovery();
        setStatusText(getString(R.string.status_searching));
        findMasterButton.setEnabled(false);
    }

    private void setStatusText(String text) {
        status.setText(text);
    }

    private void sendBatteryLocationData(final int requestCode) {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            JSONObject obj = new JSONObject();
                            try {
                                obj.put("request_code", requestCode);
                                obj.put("request_status", 200);
                                obj.put("name", "slave device");
                                obj.put("batteryLevel", getBatteryStatus());
                                obj.put("latitude", location.getLatitude());
                                obj.put("longitude", location.getLongitude());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            Log.println(Log.INFO, "payload : ", obj.toString());
                            connectionsClient.sendPayload(
                                    masterEndpointId, Payload.fromStream(new ByteArrayInputStream(obj.toString().getBytes(UTF_8))));
                        }
                    }
                });
    }

}