package jp.co.study.yis.blesample1;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    Button button_scan_start = null;
    Button button_scan_stop = null;
    // Handlerはそれを生成したスレッド上で処理を実行するためのクラス。
    private Handler mHandler;

    /*
     * BLE権限関係のコード
     */

    // Bluetooth権限リクエスtに関する定数
    final int REQUEST_BLEPERMISSIONS = 1;
    // Bluetooth昨日有効化リクエスとに関する定数
    final int REQUEST_ENABLE_BT=1001;

    final int BLE_DEVICE_NOT_EXISTS = -1;
    final int BLE_PERMISSION_REQUEST_FAILED = -2;
    final int BLE_FUNCTION_DISABLED = -3;

    // SKDバージョンチェック用定数
    final  int SDKVER_MARSHMALLOW = 23;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mbleScanner;
    private ScanCallback mScanCallback;

    // Bleのセットアップを行う。
    // 権限チェックや権限リクエスとを行った後、
    // Bluetooth LowEnergyを使うには、AndroidManifest.xmlに以下の権限取得要求を記載しなければならない。
    // android.permission.BLUETOOTH
    // android.permission.BLUETOOTH_ADMIN
    // android.permission.ACCESS_COARSE_LOCATION
    private void ble_setup()
    {
        // デバイスがBLEに対応していなければトースト表示.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ble_setup_failed_with_finish(BLE_DEVICE_NOT_EXISTS);
        }
        // Android6.0以降なら権限確認.
        // 古い端末であれば権限はインストール時に取得済みであるはず・・
        if(Build.VERSION.SDK_INT >= SDKVER_MARSHMALLOW)
        {
            // 権限が許可されていない場合はリクエスト.
            if(PermissionChecker.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
                // リクエストの結果はonRequestPermissionResultに帰ってくる
                requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_BLEPERMISSIONS);
            }
        }else {
            // 権限チェックが完了した場合は、Bletooth昨日の有効化を行う。
            try_ble_enable(false);
        }
    }

    private void ble_setup_failed_with_finish(int errcode){
        HashMap<Integer,String> message = new HashMap<>();
        message.put(BLE_DEVICE_NOT_EXISTS,"Blue Tooth Low Energy未対応デバイスです");
        message.put(BLE_PERMISSION_REQUEST_FAILED,"このプログラムはBleToothを使用します。");

        String msg = "アプリケーションエラーです。(" + errcode + ")";
        if(message.containsKey(errcode)){
            msg = message.get(errcode);
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }




    private void try_ble_enable(boolean isRetry)
    {
        /*
         * BlueToothAdapterの取得
         */
        final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            if(!isRetry) {
                // 最初の呼び出してBlueToothAdapterが取得できなければBlueToothの有効化を促す。
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }else {
                // BlueToothAdapterが取得できなければ終了
                ble_setup_failed_with_finish(BLE_FUNCTION_DISABLED);
            }
        }
        mbleScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // スキャンコールバック
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.i("fukami","ScanCallback.onScanResult");
                super.onScanResult(callbackType, result);
                on_scan_result(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                Log.i("fukami","ScanCallback.onBatchScanResults");
                super.onBatchScanResults(results);
                for(ScanResult r:results){
                    on_scan_result(r);
                }
            }

            // スキャンが開始できなかった時に呼ばれる。
            @Override
            public void onScanFailed(int errorCode) {
                Log.i("fukami","ScanCallback.onScanFailed");
                super.onScanFailed(errorCode);
            }
        };

        // ble実行可能
        ble_setup_suceeded();
    }

    void on_scan_result(ScanResult result){
        Log.i("fukami","On Scan Result");
        byte [] bytes;
        int rssi=-9999;
        BluetoothDevice device;
        ScanRecord scanRecord;

        if(result == null){
            return;
        }

        if( (device = result.getDevice()) == null){
            return;
        }

        if( (scanRecord =result.getScanRecord()) == null){
            return;
        }
        if( (bytes =scanRecord.getBytes())== null || bytes.length == 0){
            return;
        }

        rssi = result.getRssi();

        String deviceName = device.getName();

        String uuid = getUUID(bytes);
        String major = getMajor(bytes);
        String minor = getMinor(bytes);

        Log.i("fukami",deviceName + ":" + uuid + ":"+major+":"+minor);

    }
    private String getUUID(byte[] scanRecord) {
        String uuid = IntToHex2(scanRecord[9] & 0xff)
            + IntToHex2(scanRecord[10] & 0xff)
            + IntToHex2(scanRecord[11] & 0xff)
            + IntToHex2(scanRecord[12] & 0xff)
            + "-"
            + IntToHex2(scanRecord[13] & 0xff)
            + IntToHex2(scanRecord[14] & 0xff)
            + "-"
            + IntToHex2(scanRecord[15] & 0xff)
            + IntToHex2(scanRecord[16] & 0xff)
            + "-"
            + IntToHex2(scanRecord[17] & 0xff)
            + IntToHex2(scanRecord[18] & 0xff)
            + "-"
            + IntToHex2(scanRecord[19] & 0xff)
            + IntToHex2(scanRecord[20] & 0xff)
            + IntToHex2(scanRecord[21] & 0xff)
            + IntToHex2(scanRecord[22] & 0xff)
            + IntToHex2(scanRecord[23] & 0xff)
            + IntToHex2(scanRecord[24] & 0xff);
        return uuid;
    }

    private String getMajor(byte[] scanRecord) {
        String hexMajor = IntToHex2(scanRecord[25] & 0xff) + IntToHex2(scanRecord[26] & 0xff);
        return String.valueOf(Integer.parseInt(hexMajor, 16));
    }

    private String getMinor(byte[] scanRecord) {
        String hexMinor = IntToHex2(scanRecord[27] & 0xff) + IntToHex2(scanRecord[28] & 0xff);
        return String.valueOf(Integer.parseInt(hexMinor, 16));
    }

    // 16進2桁に変換
    private String IntToHex2(int i) {
        char hex_2[]  = {
            Character.forDigit((i >> 4) & 0x0f, 16),
            Character.forDigit(i & 0x0f, 16)
        };

        String hex_2_str = new String(hex_2);
        return hex_2_str.toUpperCase();
    }
    /**
     * bleのセットアップが全て完了した時に呼ばれる
     */
    private void ble_setup_suceeded(){
        button_scan_start.setEnabled(true);
    }
    /*
     * Bluetooth スキャン関連
     */
    private void ble_scan_start()
    {

        try_ble_enable(false);
        mbleScanner.startScan(mScanCallback);
        button_scan_start.setEnabled(false);
        button_scan_stop.setEnabled(true);
    }
    private void ble_scan_stop()
    {
        mbleScanner.stopScan(mScanCallback);
        button_scan_start.setEnabled(true);
        button_scan_stop.setEnabled(false);
    }

    /*
     * Activity オーバーライド
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // 権限リクエストの結果を取得する.
        if (requestCode == REQUEST_BLEPERMISSIONS) {
            for(int result : grantResults){
                if(result != PackageManager.PERMISSION_GRANTED){

                    ble_setup_failed_with_finish(BLE_PERMISSION_REQUEST_FAILED);
                }
            }
            // 権限がある場合はBlueTooth関係のオブジェクトをセットアップ
            try_ble_enable(false);
        }else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    // startActivityForResult で起動させたアクティビティが
    // finish() により破棄されたときにコールされる
    // requestCode : startActivityForResult の第二引数で指定した値が渡される
    // resultCode : 起動先のActivity.setResult の第一引数が渡される
    // Intent data : 起動先Activityから送られてくる Intent
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    // Bluetoothが有効にされたら再度mBluetoothAdapterの取得を試みる。
                    try_ble_enable(true);
                } else if (resultCode == RESULT_CANCELED) {
                    // 権限が付与されなかった。
                    finish();
                }
                break;

            default:
                break;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button_scan_start =(Button)findViewById(R.id.scan_start_button);
        button_scan_start.setEnabled(false);
        button_scan_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.ble_scan_start();
            }
        });
        button_scan_stop = (Button)findViewById(R.id.scan_stop_button);
        button_scan_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.ble_scan_stop();
            }
        });

        ble_setup();

    }
    @Override
    protected void onPause()
    {
        ble_scan_stop();
        super.onPause();
    }
    @Override
    protected void onResume()
    {
        ble_scan_start();
        super.onResume();
    }
    @Override
    protected void onDestroy()
    {
        ble_scan_stop();
        super.onDestroy();
    }

}

interface IBLEPerser
{
    public void Parse(BluetoothDevice device, int rssi, byte[] scanRecord);
}

class BeaconPerser implements  IBLEPerser
{

    @Override
    public void Parse(BluetoothDevice device, int rssi, byte[] scanRecord) {

    }
}