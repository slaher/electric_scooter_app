package com.example.electricscooterapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.util.Pair;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends AppCompatActivity  {
    private BluetoothSocket socket;
    private BluetoothDevice mDevice;

    private OutputStream outputStream;
    private InputStream inputStream;
    private ListView listDevices;
    private Button discoverDevicesBtn;
    private Switch switchScooterOnOff;
    private Switch switchBluetoothOnOff;
    private Switch switchSpeedLimitOnOff;

    private List<String> listOfNames = new ArrayList<>();
    private List<Pair<String, String>> sList = new ArrayList<>();
    private BluetoothAdapter mBluetoothAdapter;
    private Enablers enablers = new Enablers();
    private BluetoothChangeStateEvent mBluetoothChangeStateEvent = new BluetoothChangeStateEvent();
    private Controls mControls = new Controls();
    private ArrayAdapter adapter;

    //    scooter virtual key
    private String strEnable = "enable";
    private String strDisable = "disable";
    private String strScooterStatusEnabled = "my_state_is_enabled";
    private String strScooterStatusDisabled = "my_state_is_disabled";

    //    speed limit
    private String strSpeedLimitOn = "speed_limit_on";
    private String strSpeedLimitOff = "speed_limit_off";
    private String strSpeedLimitStatusEnabled = "speed_limit_is_enabled";
    private String strSpeedLimitStatusDisabled = "speed_limit_is_disabled";

    //    key and speed limit states
    private String getStates = "get_states";

    private Thread readDataThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;
    private IntentFilter filterBluetoothAdapter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    private IntentFilter filterBluetoothDevice = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // controls
        listDevices = findViewById(R.id.lista);
        discoverDevicesBtn = findViewById(R.id.button);
        switchScooterOnOff = findViewById(R.id.switch_scooter_on_off);
        switchBluetoothOnOff = findViewById(R.id.bluetooth_on_off);
        switchSpeedLimitOnOff = findViewById(R.id.switch_speed_limit);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Bluetooth_enable_disable_action(enablers.isBluetoothEnabled(mBluetoothAdapter));
        mControls.textInformation.setText("Not connected device");

        BroadcastReceiver mReceiverBluetoothAdapter = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mBluetoothChangeStateEvent.ReceiveBluetoothChangeStateEvent(intent);
                boolean is_bluetooth_Enabled = Objects.requireNonNull(intent.getExtras()).getBoolean("is_bluetooth_enabled");
                Bluetooth_enable_disable_action(is_bluetooth_Enabled);
            }
        };

        BroadcastReceiver mReceiverBluetoothDevice = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mBluetoothChangeStateEvent.ReceiveBluetoothDeviceEvent(intent);
                boolean is_bluetooth_device_connected =
                        Objects.requireNonNull(intent.getExtras()).getBoolean("is_bluetooth_device_connected");
                if (!is_bluetooth_device_connected)
                {
                    SocketAndStreamClose();
                    switchScooterOnOff.setChecked(false);
                    switchScooterOnOff.setEnabled(false);
                    switchSpeedLimitOnOff.setChecked(false);
                    switchSpeedLimitOnOff.setEnabled(false);

                    ClearAdapterList("CONNECTION LOST");
                }
            }
        };

        switchScooterOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SwitchScooterOnOffCheckedChange(isChecked);
            }
            });

        switchSpeedLimitOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SwitchSpeedLimitOnOffCheckedChange(isChecked);
            }
        });

        switchBluetoothOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SwitchBluetoothOnOffCheckedChange(isChecked);
            }
        });

        registerReceiver(mReceiverBluetoothAdapter, filterBluetoothAdapter);
        registerReceiver(mReceiverBluetoothDevice, filterBluetoothDevice);
    }

    private void SwitchScooterOnOffCheckedChange(boolean isChecked)
    {
        if (socket != null) {
            if (socket.isConnected()) {
                try {
                    if (isChecked) SendMessage(strEnable);
                    else SendMessage(strDisable);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void SwitchSpeedLimitOnOffCheckedChange(boolean isChecked)
    {
        if (socket != null) {
            if (socket.isConnected()) {
                try {
                    if (isChecked) SendMessage(strSpeedLimitOn);
                    else SendMessage(strSpeedLimitOff);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void SwitchBluetoothOnOffCheckedChange(boolean isChecked)
    {
        if (isChecked) {
            EnableBluetooth();
            if (!enablers.isBluetoothEnabled(mBluetoothAdapter))
                switchBluetoothOnOff.setChecked(false);
        }
        else
            DisableBluetooth();
    }

    private void EnableBluetooth() {
        if (!enablers.isBluetoothEnabled(mBluetoothAdapter)) {
            Intent eintent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(eintent, 1);
        }
    }

    private void DisableBluetooth() {
        if (enablers.isBluetoothEnabled(mBluetoothAdapter)) mBluetoothAdapter.disable();
    }

    private void Bluetooth_enable_disable_action(boolean isBluetoothEnabled)
    {
        if (isBluetoothEnabled)
        {
            switchBluetoothOnOff.setChecked(true);
            switchScooterOnOff.setEnabled(false);
            switchSpeedLimitOnOff.setChecked(false);
            switchSpeedLimitOnOff.setEnabled(false);
            discoverDevicesBtn.setText("SHOW PAIRED DEVICES");
            switchScooterOnOff.setChecked(false);
            discoverDevicesBtn.setEnabled(true);
        }

        else{
            SocketAndStreamClose();
            switchScooterOnOff.setChecked(false);
            switchScooterOnOff.setEnabled(false);
            switchSpeedLimitOnOff.setChecked(false);
            switchSpeedLimitOnOff.setEnabled(false);
            switchBluetoothOnOff.setChecked(false);
            discoverDevicesBtn.setEnabled(false);
            discoverDevicesBtn.setText("BLUETOOTH DISABLED");
            ClearAdapterList("");
        }
    }

    public void ListOfDevices(View view){
        SocketAndStreamClose();

        mControls.textInformation.setText("Not connected device");
        switchScooterOnOff.setEnabled(false);
        switchSpeedLimitOnOff.setEnabled(false);
        ClearAdapterList();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice bt : pairedDevices) {
            sList.add(new Pair<>(bt.getName() + " - " + bt.getAddress(), bt.getAddress()));
            listOfNames.add(bt.getName() + " - " + bt.getAddress());
        }
        adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, listOfNames);
        listDevices.setAdapter(adapter);

        listDevices.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object obj = listDevices.getAdapter().getItem(position);
                String value = obj.toString();
                listDevices.setAdapter(adapter);
                ClearAdapterList();

                for (Pair entry : sList)
                {
                    if (entry.first.toString().equals(value))
                    {
                        try {
                            Connect(entry.second.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[50];
        readDataThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = inputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            inputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            if (data.equals(strScooterStatusEnabled))
                                                switchScooterOnOff.setChecked(true);
                                            if (data.equals(strScooterStatusDisabled))
                                                switchScooterOnOff.setChecked(false);
                                            if (data.equals(strSpeedLimitStatusEnabled))
                                                switchSpeedLimitOnOff.setChecked(true);
                                            if (data.equals(strSpeedLimitStatusDisabled))
                                                switchSpeedLimitOnOff.setChecked(false);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        readDataThread.start();
    }

    private void ClearAdapterList()
    {
        if (adapter != null)
            adapter.clear();
    }

    private void ClearAdapterList(String text)
    {
        if (adapter != null)
        {
            adapter.clear();
            mControls.textInformation.setText(text);
        }
    }

    private void SendMessage(String message) throws IOException {
        message += '\n';
        outputStream.write(message.getBytes());
    }

    private void SocketAndStreamClose()
    {
        if (outputStream != null){
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }}
        outputStream = null;

        if (inputStream != null){
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }}
        inputStream = null;

        if (socket != null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket = null;
    }

    private void Connect(String mac) throws IOException {
        mDevice = mBluetoothAdapter.getRemoteDevice(mac);
        if (socket == null || !socket.isConnected())
        {
            ParcelUuid[] uuids = mDevice.getUuids();
            socket = mDevice.createRfcommSocketToServiceRecord(uuids[0].getUuid());
            socket.connect();
            mControls.textInformation.setText("Connected to: " + mac);

            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            beginListenForData();

            try {
                SendMessage(getStates);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        if (socket.isConnected()) {
            switchScooterOnOff.setEnabled(true);
            switchSpeedLimitOnOff.setEnabled(true);
        }
        else {
            switchScooterOnOff.setEnabled(false);
            switchSpeedLimitOnOff.setEnabled(false);
        }
    }
}
