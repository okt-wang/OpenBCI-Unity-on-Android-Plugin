package com.okt.ganglionplugin;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;


import java.util.Arrays;
import java.util.List;
import java.util.UUID;
public class BluetoothLeService extends Service {
    private final static String TAG = "Ganglion"+BluetoothLeService.class.getSimpleName();


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    public final static String FULL_DATA_1 =
            "com.example.bluetooth.le.FULL_DATA_1";
    public final static String FULL_DATA_2 =
            "com.example.bluetooth.le.FULL_DATA_2";

    public final static String DATA_TYPE =
            "com.example.bluetooth.le.RAW_DATA";
    public final static String SAMPLE_ID =
            "com.example.bluetooth.le.SAMPLE_ID";


    public final static UUID UUID_GANGLION_RECEIVE = UUID.fromString(SampleGattAttributes.UUID_GANGLION_RECEIVE);
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static int last_id;
    private static int packets_dropped;
    private static int[] lastChannelData = {0, 0, 0, 0};
    private static int[] lastAcceleromoter = {0, 0, 0};
    private static int[] lastImpedance = {0, 0, 0, 0, 0};
    private static int[] sample_id={0, 0};
    private static int[][] fullData = {{0, 0, 0, 0},{0, 0, 0, 0}};
    private static int packetID;
    private static double scale_fac_uVolts_per_count = 1200 / (8388607.0 * 1.5 * 51.0);


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG,"GattServer Services Discovered");
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    /**
     * BEGIN OF CUSTOM METHODS
     */

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        if (UUID_GANGLION_RECEIVE.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            Log.i(TAG,String.valueOf(data[0]));
            // Log.d(TAG,"First Byte" + data[0]);
            if (data != null && data.length > 0) {
                parseData(data);
                // Log.d(TAG,"packetID" + packetID);
                if (packetID == 0) {
                    intent.putExtra(DATA_TYPE, "RAW");
                    intent.putExtra(FULL_DATA_1, fullData[0]);
                    intent.putExtra(SAMPLE_ID, sample_id);
                    // Log.d(TAG,"SampleID" + sample_id[0]);
                } else if (packetID >=101 && packetID <= 200){
                    intent.putExtra(DATA_TYPE, "19BIT");
                    intent.putExtra(FULL_DATA_1, fullData[0]);
                    intent.putExtra(FULL_DATA_2, fullData[1]);
                    intent.putExtra(SAMPLE_ID, sample_id);
                    //Log.i(TAG,"SampleID1 " + sample_id[0] + "\n" + "SampleID1 " + sample_id[1]);
                } else {
                    intent.putExtra(DATA_TYPE, "INVALID");
                }
            }
        } else {
            // Handle other characteristics here

        }
        sendBroadcast(intent);
    }


    private static boolean parseData(byte[] data) {

        //Convert from -128 - 127 to 0 - 255
        if (data[0] < 0) {
            packetID = data[0] + 256;
        } else {
            packetID = data[0];
        }
        Log.wtf(TAG," PacketID " + data[0]);
        //Copy the data without the packetID to payload
        byte[] payload= Arrays.copyOfRange(data, 1, data.length);
        //Boolean receiving_ASCII;
        if (packetID == 0) {
            //receiving_ASCII = false;
            parseRaw(packetID, payload);
            // 18-bit compression with Acceleromete
        } else if (packetID >= 1 && packetID <= 100){
            //receiving_ASCII = false;
            //parse18bit(packetID, unpac[1:])
            //19-bit compression without Accelerometer
        } else if(packetID >=101 && packetID <= 200){
            //receiving_ASCII = false;
            //packetID-100 for sampleID calculation
            // parse19bit(packetID-100, test);
            parse19bit(packetID-100, payload);
            //Impedance Channel
        } else if (packetID >= 201 && packetID <= 205){
            // receiving_ASCII = false;
            //TODO parseImpedance(packetID, packet[1:])
            //Part of ASCII -- TODO: better formatting of incoming ASCII
        } else if( packetID == 206){
            //print("%\t" + str(packet[1:]))
            //receiving_ASCII = true;
            // time_last_ASCII = timeit.default_timer()
            //End of ASCII message
        } else if (packetID == 207){
            // print("%\t" + str(packet[1:]))
            // print ("$$$")
            //receiving_ASCII = false;
        } else{
            //  print("Warning: unknown type of packet: " + str(packetID))
            return false;
        }
        return true;
    }

    private static void parseRaw(int packetID, byte[] payload){
        //Dealing with "Raw uncompressed" - 24 Bit
        if (payload.length != 19){
            Log.e(TAG, "Wrong size, for Raw data " + payload.length + " instead of 19 bytes");
            return;
        }

        // 4 channels of 24bits - 4*3=12 Bytes of 19 Bytes used
        //Take values one by one
        for(int i=0;  i<12;  i=i+3){
            lastChannelData[i/3]=conv24bitsToInt(Arrays.copyOfRange(payload, i, i+3));
            fullData[0][i/3]=lastChannelData[i/3];
            fullData[1][i/3]=0;
        }
        sample_id[0]=1;
        sample_id[1]=0;
        updatePacketsCount(packetID);
    }

    private static void parse19bit(int packetID, byte[] payload){
        //Dealing with "19-bit compression without Accelerometer"

        if (payload.length != 19){
            Log.e(TAG, "Wrong size, for 19-bit compression data " + payload.length + " instead of 19 bytes");
            return ;
        }
        //should get 2 by 4 arrays of uncompressed data
        int[][] deltas = decompressDeltas19Bit(payload);
        // the sample_id will be shifted
        int delta_id = 0;

        for ( int i = 0; i < 2; i++ ) {
            for ( int j = 0; j < 4; j++ ) {
                fullData[i][j]=lastChannelData[j] - deltas[i][j];
                lastChannelData[j]=fullData[i][j];
            }
            //convert from packet to sample id
            sample_id[delta_id] = packetID * 2 + delta_id;
            //19bit packets hold deltas between two samples
            delta_id++;
            updatePacketsCount(packetID);

        }
        //Log.d(TAG,"data" + fullData);
    }

    private static void updatePacketsCount(int packetID){
        // Update last packet ID and dropped packets
        if (last_id == -1){
            last_id = packetID;
            packets_dropped  = 0;
            return;
        }
        // ID loops every 101 packets
        if (packetID > last_id){
            packets_dropped = packetID - last_id - 1;
        } else{
            packets_dropped = packetID + 101 - last_id - 1;
        }
        last_id = packetID;
        //if (packets_dropped > 0)
        //  Log.e(TAG, "Warning: dropped " + packets_dropped + " packets.");
    }

    private static int[][] decompressDeltas19Bit(byte[] payload){
        /*
        Called to when a compressed packet is received.
        payload: Just the data portion of the sample. So 19 bytes.
        return {Array} - An array of deltas of shape 2x4 (2 samples per packet and 4 channels per sample.)
        */
        if (payload.length != 19){
            Log.e(TAG,"Input should be 19 bytes long.");
        }

        int[][] receivedDeltas= {{0, 0, 0, 0},
                {0, 0, 0, 0}};

        //Sample 1 - Channel 1
        int[] miniBuf = {
                ( (payload[0] & 0xFF) >>> 5),
                (((payload[0] & 0x1F) << 3) & 0xFF) | ( (payload[1] & 0xFF) >>> 5),
                (((payload[1] & 0x1F) << 3) & 0xFF) | ( (payload[2] & 0xFF) >>> 5)
        };
        receivedDeltas[0][0] = conv19bitToInt32( miniBuf);

        //Sample 1 - Channel 2
        miniBuf = new int[]{
                (payload[2] & 0x1F) >>> 2,
                (payload[2] << 6 & 0xFF) | ( (payload[3] & 0xFF) >>> 2),
                (payload[3] << 6 & 0xFF) | ( (payload[4] & 0xFF) >>> 2)
        };

        receivedDeltas[0][1] = conv19bitToInt32(miniBuf);

        //Sample 1 - Channel 3
        miniBuf = new int[]{
                ((payload[4] & 0x03) << 1 & 0xFF) | ( (payload[5] & 0xFF) >>> 7),
                ((payload[5] & 0x7F) << 1 & 0xFF) | ( (payload[6] & 0xFF) >>> 7),
                ((payload[6] & 0x7F) << 1 & 0xFF) | ( (payload[7] & 0xFF) >>> 7)
        };
        receivedDeltas[0][2] = conv19bitToInt32(miniBuf);

        //Sample 1 - Channel 4
        miniBuf = new int[]{
                ((payload[7] & 0x7F) >>> 4),
                ((payload[7] & 0x0F) << 4 & 0xFF) | ( (payload[8] & 0xFF) >>> 4),
                ((payload[8] & 0x0F) << 4 & 0xFF) | ( (payload[9] & 0xFF) >>> 4)
        };
        receivedDeltas[0][3] = conv19bitToInt32(miniBuf);

        //Sample 2 - Channel 1
        miniBuf = new int[]{
                ((payload[9] & 0x0F) >>> 1),
                (payload[9] << 7 & 0xFF) | ( (payload[10] & 0xFF) >>> 1),
                (payload[10] << 7 & 0xFF) | ( (payload[11] & 0xFF) >>> 1)
        };
        receivedDeltas[1][0] = conv19bitToInt32(miniBuf);

        //Sample 2 - Channel 2
        miniBuf = new int[]{
                ((payload[11] & 0x01) << 2 & 0xFF) | ( (payload[12] & 0xFF) >>> 6),
                (payload[12] << 2 & 0xFF) | ( (payload[13] & 0xFF) >>> 6),
                (payload[13] << 2 & 0xFF) | ( (payload[14] & 0xFF) >>> 6)
        };
        receivedDeltas[1][1] = conv19bitToInt32(miniBuf);

        // Sample 2 - Channel 3
        miniBuf = new int[]{
                ((payload[14] & 0x38) >>> 3),
                ((payload[14] & 0x07) << 5 & 0xFF) | ((payload[15] & 0xF8) >>> 3),
                ((payload[15] & 0x07) << 5 & 0xFF) | ((payload[16] & 0xF8) >>> 3)
        };
        receivedDeltas[1][2] = conv19bitToInt32(miniBuf);

        // Sample 2 - Channel 4
        miniBuf = new int[]{
                (payload[16] & 0x07),
                (payload[17] & 0xFF),
                (payload[18] & 0xFF)};
        receivedDeltas[1][3] = conv19bitToInt32(miniBuf);

        for(int i = 0; i < 2;i++){
            for(int j = 0; j < 4;j++)
                Log.i(TAG,"data" + i + " " + j + " " + Double.valueOf(scale_fac_uVolts_per_count * receivedDeltas[i][j]));
        }

        return receivedDeltas;
    }

    private static int conv19bitToInt32(int[] threeByteBuffer) {
        // Convert 19bit data coded on 3 bytes to a proper integer (LSB bit 1 used as sign). """
        if (threeByteBuffer.length != 3) {
            Log.e(TAG, "Input should be 3 bytes long.");
            return -1;
        }
        int prefix = 0b0000000000000;
        //if LSB is 1, negative number, some hasty unsigned to signed conversion to do
        if ((threeByteBuffer[2] & 0x01 ) > 0) {
            prefix = 0b1111111111111;
            //.d(TAG,"data1 " + ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]));
            return ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        } else {
            //Log.d(TAG,"data2 " + ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]));
            return ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        }
    }

    private static int conv24bitsToInt(byte[] byteArray){
        //Convert 24bit data coded on 3 bytes to a proper integer """
        if (byteArray.length != 3){
            // Log.e(TAG, "Input should be 3 bytes long.");
            return -1;
        }

        int newInt = (((0xFF & byteArray[0]) << 16) | ((0xFF & byteArray[1]) << 8) | (0xFF & byteArray[2])
        );

        //If the MSB is 1 then the number is negative - fill up with 1s
        if ((newInt & 0x00800000) > 0) {
            newInt |= 0xFF000000;
        } else {
            newInt &= 0x00FFFFFF;
        }

        return newInt;
    }


    /**
     * END OF CUSTOM METHODS**************************************************************************
     * END OF CUSTOM METHODS**************************************************************************
     * END OF CUSTOM METHODS**************************************************************************
     */

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        Log.i(TAG,"Connecting to GATT Server on the Device");
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.i(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void onCharacteristicWrite (BluetoothGatt gatt,
                                       BluetoothGattCharacteristic characteristic,
                                       int status){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.w(TAG, "Written to: " +characteristic.getUuid() + " Status: "+ (mBluetoothGatt.GATT_SUCCESS==status));
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic){
        //pre-prepared characteristic to write to
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.i(TAG, "Writing to " +characteristic.getUuid());

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        Log.i(TAG,characteristic.getUuid()+" - Notify:"+enabled);
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}