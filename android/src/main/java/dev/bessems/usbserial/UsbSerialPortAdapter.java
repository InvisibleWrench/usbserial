package dev.bessems.usbserial;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

//import com.felhr.usbserial.UsbSerialDevice;
//import com.felhr.usbserial.UsbSerialInterface;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.util.concurrent.Executors;


import java.io.IOException;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class UsbSerialPortAdapter implements MethodCallHandler, EventChannel.StreamHandler, SerialInputOutputManager.Listener {

    private final String TAG = UsbSerialPortAdapter.class.getSimpleName();

    private int m_InterfaceId;
    private UsbDeviceConnection m_Connection;
    private UsbSerialPort m_SerialDevice;
    private Registrar m_Registrar;
    private String m_MethodChannelName;
    private EventChannel.EventSink m_EventSink;
    private Handler m_handler;

    private SerialInputOutputManager usbIoManager;

    private static final int WRITE_WAIT_MILLIS = 2;
    private static final int READ_WAIT_MILLIS = 2;

    UsbSerialPortAdapter(Registrar registrar, int interfaceId, UsbDeviceConnection connection, UsbSerialPort serialDevice) {
        m_Registrar = registrar;
        m_InterfaceId = interfaceId;
        m_Connection = connection;
        m_SerialDevice = serialDevice;
        m_MethodChannelName = "usb_serial/UsbSerialPortAdapter/" + String.valueOf(interfaceId);
        m_handler = new Handler(Looper.getMainLooper());
        final MethodChannel channel = new MethodChannel(registrar.messenger(), m_MethodChannelName);
        channel.setMethodCallHandler(this);
        final EventChannel eventChannel = new EventChannel(registrar.messenger(), m_MethodChannelName + "/stream");
        eventChannel.setStreamHandler(this);
    }

    String getMethodChannelName() {
        return m_MethodChannelName;
    }

    private void setPortParameters(int baudRate, int dataBits, int stopBits, int parity) {
        try {
            m_SerialDevice.setParameters(baudRate, dataBits, stopBits, parity);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        m_SerialDevice.setBaudRate(baudRate);
//        m_SerialDevice.setDataBits(dataBits);
//        m_SerialDevice.setStopBits(stopBits);
//        m_SerialDevice.setParity(parity);
    }

    private void setFlowControl( int flowControl ) {

//        m_SerialDevice. setFlowControl(flowControl);
    }

//    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
//
//        @Override
//        public void onReceivedData(byte[] arg0)
//        {
//            if ( m_EventSink != null ) {
//                m_handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        if ( m_EventSink != null ) {
//                            m_EventSink.success(arg0);
//                        }
//                    }
//                });
//            }
//        }
//
//    };

    private Boolean open() {
        try {
            m_SerialDevice.open(m_Connection);
            usbIoManager = new SerialInputOutputManager(m_SerialDevice, this);
            Executors.newSingleThreadExecutor().submit(usbIoManager);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

//        if ( m_SerialDevice.open() ) {
//            m_SerialDevice.read(mCallback);
//            return true;
//        } else {
//            return false;
//        }
    }

    private Boolean close() {
//        m_SerialDevice.close();
        usbIoManager.stop();
        usbIoManager = null;
        try {
            m_SerialDevice.close();
        } catch (IOException ignored) {}
        m_SerialDevice = null;
        return true;

    }

    private void write( byte[] data ) {
        try {
            m_SerialDevice.write(data, WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // return true if the object is to be kept, false if it is to be destroyed.
    public void onMethodCall(MethodCall call, Result result) {

        switch (call.method) {
            case "close":
                result.success(close());
                break;
            case "open":
                result.success(open());
                break;
            case "write":
                write((byte[])call.argument("data"));
                result.success(true);
                break;

            case "setPortParameters":
                setPortParameters((int) call.argument("baudRate"), (int) call.argument("dataBits"),
                        (int) call.argument("stopBits"), (int) call.argument("parity"));
                result.success(null);
                break;

            case "setFlowControl":
                setFlowControl((int) call.argument("flowControl"));
                result.success(null);
                break;

            case "setDTR": {
                boolean v = call.argument("value");
                try {
                    m_SerialDevice.setDTR(v);

                if (v == true) {
                    Log.e(TAG, "set DTR to true");
                } else {
                    Log.e(TAG, "set DTR to false");
                }
                result.success(null);
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error(e.toString(), e.getLocalizedMessage(), null);
                }
                break;
            }
            case "setRTS": {
                boolean v = call.argument("value");
                try {
                    m_SerialDevice.setRTS(v);
                    result.success(null);
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error(e.toString(), e.getLocalizedMessage(), null);
                }

                break;
            }

            default:
                result.notImplemented();
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        m_EventSink = eventSink;

    }

    @Override
    public void onCancel(Object o) {
        m_EventSink = null;

    }


    @Override
    public void onNewData(byte[] data) {
//        if ( m_EventSink != null ) {
//            m_EventSink.success(data);
//        }

//        if ( m_EventSink != null ) {
                m_handler.post(() -> {
                    if ( m_EventSink != null ) {
                        m_EventSink.success(data);
                    }
                });
//            }
    }

    @Override
    public void onRunError(Exception e) {

    }
}