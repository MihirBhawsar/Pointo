package com.health.pointo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.health.pointo.databinding.ActivityBluetoothBinding

@SuppressLint("MissingPermission")
class BluetoothActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var devicesAdapter: ArrayAdapter<String>
    private val devicesList = mutableListOf<String>()
    private val devicesMap = mutableMapOf<String, BluetoothDevice>()
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var permissions: Array<String>
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var connectedDevice: BluetoothDevice

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            if (!devicesMap.containsKey(device.address)) {
                device.name?.let {
                    devicesList.add(it)
                    devicesMap[device.address] = device
                    devicesAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                val device = result.device
                if (!devicesMap.containsKey(device.address)) {
                    device.name?.let {
                        devicesList.add(it)
                        devicesMap[device.address] = device
                        devicesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(
                this@BluetoothActivity,
                "Scan failed with error: $errorCode",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private lateinit var binding: ActivityBluetoothBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        createPermissionArray()
        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devicesList)
        binding.listViewDevices.adapter = devicesAdapter

        binding.buttonStartScan.setOnClickListener {
            binding.listViewDevices.visibility = View.GONE
            binding.textViewDevicesDeatils.visibility = View.VISIBLE
            checkPermissionsAndStartScan()
        }

        binding.listViewDevices.setOnItemClickListener { _, _, position, _ ->
            val deviceName = devicesList[position]
            val device = devicesMap.values.firstOrNull { it.name == deviceName }
            device?.let {
                connectToDevice(it)
            }
        }

        enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startScan()
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth is required to scan for devices",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    startScan()
                } else {
                    Toast.makeText(
                        this,
                        "Permissions are required to scan for devices",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.listViewDevices.visibility == View.VISIBLE) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    onDeviceDisconnect()
                    bluetoothGatt?.disconnect()
                }
            }
        })
    }

    private fun createPermissionArray() {
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndStartScan() {
        if (hasPermissions(this, permissions)) {
            startScan()
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    private fun startScan() {
        try {
            bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopScan() {
        try {
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectedDevice = device
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                onDeviceConnect()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onDeviceDisconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                displayGattServices(gatt.services)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                displayCharacteristic(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            displayCharacteristic(characteristic)
        }
    }

    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        gattServices?.forEach { gattService ->
            gattService.characteristics.forEach { characteristic ->
                bluetoothGatt?.readCharacteristic(characteristic)
            }
        }
    }

    private fun displayCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val data = characteristic.value
        val dataString = data?.joinToString(", ") { String.format("%02X", it) } ?: "No data"
        val uuidsString = connectedDevice.uuids?.joinToString(", ") { it.uuid.toString() } ?: "No UUIDs available"

        runOnUiThread {
            binding.textViewDevicesDeatils.text = """
            Connected Device Name: ${connectedDevice.name}
            Address: ${connectedDevice.address}
            Type: ${connectedDevice.type}
            UUIDs: $uuidsString
            Characteristic Data: $dataString
        """.trimIndent()
        }
    }


    private fun onDeviceConnect() {
        runOnUiThread {
            Toast.makeText(
                this@BluetoothActivity,
                "Device connected",
                Toast.LENGTH_SHORT
            ).show()
            binding.listViewDevices.visibility = View.GONE
            binding.textViewDevicesDeatils.visibility = View.VISIBLE
        }
    }

    private fun onDeviceDisconnect() {
        runOnUiThread {
            Toast.makeText(
                this@BluetoothActivity,
                "Disconnected from device",
                Toast.LENGTH_SHORT
            ).show()
            binding.listViewDevices.visibility = View.VISIBLE
            binding.textViewDevicesDeatils.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter.isEnabled) {
            if (hasPermissions(this, permissions)) {
                startScan()
            }
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
        bluetoothGatt?.disconnect()
    }
}
