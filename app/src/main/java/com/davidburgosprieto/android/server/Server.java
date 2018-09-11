package com.davidburgosprieto.android.server;

import android.content.Context;
import android.os.Build;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

public class Server {
    private static final String TAG = Server.class.getSimpleName();
    private static final int socketServerPORT = 8080;

    private MainActivity mActivity;
    private ServerSocket mServerSocket;
    private String mMessage = "";
    private TelephonyManager mTelephonyManager;
    private ServiceState mServiceState;

    public Server(MainActivity activity) {
        mActivity = activity;
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();

        // Define the TelephonyManager.
        mTelephonyManager = (TelephonyManager) mActivity.getApplicationContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        if (mTelephonyManager != null) {
            // Get current service state.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    mServiceState = mTelephonyManager.getServiceState();
                } catch (SecurityException e) {
                    Log.e(TAG, mActivity.getBaseContext().getResources().getString(
                            R.string.get_service_state_error) + e);
                }
            }

            // Set a listener for receiving service state changes.
            PhoneStateListener mPhoneListener = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    mServiceState = serviceState;
                    super.onServiceStateChanged(serviceState);
                }
            };
            mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    public int getPort() {
        return socketServerPORT;
    }

    public void onDestroy() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, mActivity.getBaseContext().getResources().getString(
                        R.string.on_destroy_error) + e);
            }
        }
    }

    private class SocketServerThread extends Thread {
        int count = 0;

        @Override
        public void run() {
            try {
                mServerSocket = new ServerSocket(socketServerPORT);

                while (true) {
                    final Socket socket = mServerSocket.accept();
                    count++;
                    mMessage += "#" + count + " " +
                            mActivity.getBaseContext().getResources().getString(R.string.from)
                            + " " + socket.getInetAddress() + ":"
                            + socket.getPort() + "\n";

                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mActivity.mMessageTextView.setText(mMessage);
                        }
                    });

                    // Reply to connection.
                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
                            socket, count);
                    socketServerReplyThread.run();
                }
            } catch (IOException e) {
                Log.e(TAG, mActivity.getBaseContext().getResources().getString(
                        R.string.get_socket_server_thread_run_error) + e);
            }
        }
    }

    private class SocketServerReplyThread extends Thread {
        private Socket hostThreadSocket;
        int cnt;

        SocketServerReplyThread(Socket socket, int c) {
            hostThreadSocket = socket;
            cnt = c;
        }

        @Override
        public void run() {
            final OutputStream outputStream;
            String msgReply = mActivity.getBaseContext().getResources().getString(R.string.hello)
                    + " " + cnt;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(getRadio());
                printStream.close();

                mMessage += mActivity.getBaseContext().getResources().getString(R.string.reply) +
                        msgReply + "\n\n";
                mActivity.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mActivity.mMessageTextView.setText(mMessage);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, mActivity.getBaseContext().getResources().getString(
                        R.string.get_socket_server_reply_thread_run_error) + e);
                mMessage += mActivity.getBaseContext().getResources().getString(R.string.wrong) +
                        e.toString() + "\n";
            }

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.mMessageTextView.setText(mMessage);
                }
            });
        }
    }

    public String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress
                            .nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += mActivity.getBaseContext().getResources().getString(R.string.running)
                                + inetAddress.getHostAddress();
                    }
                }
            }

        } catch (SocketException e) {
            Log.e(TAG, mActivity.getBaseContext().getResources().getString(
                    R.string.get_ip_address_error) + e);
            ip += mActivity.getBaseContext().getResources().getString(R.string.wrong) +
                    e.toString() + "\n";
        }
        return ip;
    }

    private JSONObject getRadio() {
        final JSONObject radioJSONObject = new JSONObject();

        if (mTelephonyManager != null) {
            try {
                // Get data from TelephonyManager.
                radioJSONObject.put("IMEI", mTelephonyManager.getDeviceId());
                radioJSONObject.put("softwareVersion", mTelephonyManager.getDeviceSoftwareVersion());

                switch (mTelephonyManager.getPhoneType()) {
                    case (TelephonyManager.PHONE_TYPE_CDMA):
                        radioJSONObject.put("phoneType", "CDMA");
                        break;
                    case (TelephonyManager.PHONE_TYPE_GSM):
                        radioJSONObject.put("phoneType", "GSM");
                        break;
                    case (TelephonyManager.PHONE_TYPE_NONE):
                        radioJSONObject.put("phoneType", "NONE");
                        break;
                    case (TelephonyManager.PHONE_TYPE_SIP):
                        radioJSONObject.put("phoneType", "SIP");
                        break;
                    default:
                        radioJSONObject.put("phoneType", "UNKNOWN");
                }

                if (mServiceState != null && mServiceState.getState() != ServiceState.STATE_IN_SERVICE) {
                    // Phone is not in service. Get some parameters from ServiceState.
                    switch (mServiceState.getState()) {
                        case (ServiceState.STATE_EMERGENCY_ONLY):
                            radioJSONObject.put("serviceState", "EMERGENCY ONLY");
                            break;
                        case (ServiceState.STATE_OUT_OF_SERVICE):
                            radioJSONObject.put("serviceState", "OUT OF SERVICE");
                            break;
                        case (ServiceState.STATE_POWER_OFF):
                            radioJSONObject.put("serviceState", "POWER OFF");
                            break;
                        default:
                            radioJSONObject.put("serviceState", "UNKNOWN");
                    }
                    radioJSONObject.put("operatorAlphaLong", mServiceState.getOperatorAlphaLong());
                    radioJSONObject.put("roaming", mServiceState.getRoaming());
                }

                String simState;
                switch (mTelephonyManager.getSimState()) {
                    case (TelephonyManager.SIM_STATE_ABSENT):
                        simState = "ABSENT";
                        break;
                    case (TelephonyManager.SIM_STATE_CARD_IO_ERROR):
                        simState = "CARD IO ERROR";
                        break;
                    case (TelephonyManager.SIM_STATE_CARD_RESTRICTED):
                        simState = "CARD RESTRICTED";
                        break;
                    case (TelephonyManager.SIM_STATE_NETWORK_LOCKED):
                        simState = "NETWORK LOCKED";
                        break;
                    case (TelephonyManager.SIM_STATE_NOT_READY):
                        simState = "NOT READY";
                        break;
                    case (TelephonyManager.SIM_STATE_PERM_DISABLED):
                        simState = "PERM DISABLED";
                        break;
                    case (TelephonyManager.SIM_STATE_PIN_REQUIRED):
                        simState = "PIN REQUIRED";
                        break;
                    case (TelephonyManager.SIM_STATE_PUK_REQUIRED):
                        simState = "PUK REQUIRED";
                        break;
                    case (TelephonyManager.SIM_STATE_READY):
                        simState = "READY";
                        break;
                    case (TelephonyManager.SIM_STATE_UNKNOWN):
                    default:
                        simState = "UNKNOWN";
                }
                radioJSONObject.put("simState", simState);

                if (!simState.equals("ABSENT") && !simState.equals("CARD IO ERROR") &&
                        !simState.equals("UNKNOWN")) {
                    // Get SIM and network data.
                    radioJSONObject.put("simSerialNumber", mTelephonyManager.getSimSerialNumber());
                    radioJSONObject.put("simOperatorName", mTelephonyManager.getSimOperatorName());
                    radioJSONObject.put("networkOperatorName", mTelephonyManager.getNetworkOperatorName());
                    radioJSONObject.put("line1Number", mTelephonyManager.getLine1Number());

                    if (simState.equals("READY")) {
                        // Get cells info, if available.
                        JSONArray cellJSONArray = new JSONArray();
                        List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
                        if (cellInfoList != null && cellInfoList.size() > 0) {
                            boolean isValid;

                            for (CellInfo cellInfo : cellInfoList) {
                                JSONObject strengthJSONObject = new JSONObject();
                                JSONObject identityJSONObject = new JSONObject();
                                String type = "";

                                if (cellInfo instanceof CellInfoGsm) {
                                    // Cell type.
                                    isValid = true;
                                    type = "GSM";

                                    // Signal strength.
                                    CellSignalStrengthGsm cellSignalStrength =
                                            ((CellInfoGsm) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put("dbm", cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityGsm cellIdentity =
                                            ((CellInfoGsm) cellInfo).getCellIdentity();
                                    identityJSONObject.put("cid", cellIdentity.getCid());
                                    identityJSONObject.put("lac", cellIdentity.getLac());
                                    identityJSONObject.put("mccString", cellIdentity.getMccString());
                                    identityJSONObject.put("mncString", cellIdentity.getMncString());

                                } else if (cellInfo instanceof CellInfoCdma) {
                                    // Cell type.
                                    isValid = true;
                                    type = "CDMA";

                                    // Signal strength.
                                    CellSignalStrengthCdma cellSignalStrength =
                                            ((CellInfoCdma) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put("dbm", cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityCdma cellIdentity =
                                            ((CellInfoCdma) cellInfo).getCellIdentity();
                                    identityJSONObject.put("cid", cellIdentity.getBasestationId());
                                    identityJSONObject.put("lac", cellIdentity.getNetworkId());
                                    identityJSONObject.put("mcc", cellIdentity.getSystemId());
                                    identityJSONObject.put("sid", cellIdentity.getSystemId());
                                } else if (cellInfo instanceof CellInfoLte) {
                                    // Cell type.
                                    isValid = true;
                                    type = "LTE";

                                    // Signal strength.
                                    CellSignalStrengthLte cellSignalStrength =
                                            ((CellInfoLte) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put("dbm", cellSignalStrength.getDbm());
                                    strengthJSONObject.put("timingAdvance", cellSignalStrength.getTimingAdvance());

                                    // Cell Identity.
                                    CellIdentityLte cellIdentity =
                                            ((CellInfoLte) cellInfo).getCellIdentity();
                                    identityJSONObject.put("mccString", cellIdentity.getMccString());
                                    identityJSONObject.put("mncString", cellIdentity.getMncString());
                                    identityJSONObject.put("ci", cellIdentity.getCi());
                                } else if (cellInfo instanceof CellInfoWcdma) {
                                    // Cell type.
                                    isValid = true;
                                    type = "WCDMA";

                                    // Signal strength.
                                    CellSignalStrengthWcdma cellSignalStrength =
                                            ((CellInfoWcdma) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put("dbm", cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityWcdma cellIdentity =
                                            ((CellInfoWcdma) cellInfo).getCellIdentity();
                                    identityJSONObject.put("lac", cellIdentity.getLac());
                                    identityJSONObject.put("mccString", cellIdentity.getMccString());
                                    identityJSONObject.put("mncString", cellIdentity.getMncString());
                                    identityJSONObject.put("cid", cellIdentity.getCid());
                                    identityJSONObject.put("psc", cellIdentity.getPsc());
                                } else {
                                    // Unknown type of cell signal.
                                    isValid = false;
                                }

                                if (isValid) {
                                    // Add the JSON info for the current cell.
                                    JSONObject cellJSONObject = new JSONObject();
                                    cellJSONObject.put("type", type);
                                    cellJSONObject.put("isRegistered", cellInfo.isRegistered());
                                    cellJSONObject.put("strength", strengthJSONObject);
                                    cellJSONObject.put("identity", identityJSONObject);
                                    cellJSONArray.put(cellJSONObject);
                                }
                            }
                        } else {
                            // getAllCellInfo has returned null.
                            CellLocation cellLocation = mTelephonyManager.getCellLocation();
                            if (cellLocation != null) {
                                boolean isValid;
                                JSONObject identityJSONObject = new JSONObject();
                                String type = "";

                                if (cellLocation instanceof GsmCellLocation) {
                                    // Cell type.
                                    isValid = true;
                                    type = "GSM";

                                    // Cell Identity.
                                    identityJSONObject.put("cid",
                                            ((GsmCellLocation) cellLocation).getCid());
                                    identityJSONObject.put("lac",
                                            ((GsmCellLocation) cellLocation).getLac());
                                    identityJSONObject.put("psc",
                                            ((GsmCellLocation) cellLocation).getPsc());
                                } else if (cellLocation instanceof CdmaCellLocation) {
                                    // Cell type.
                                    isValid = true;
                                    type = "CDMA";

                                    // Cell Identity.
                                    identityJSONObject.put("networkId",
                                            ((CdmaCellLocation) cellLocation).getNetworkId());
                                    identityJSONObject.put("systemId",
                                            ((CdmaCellLocation) cellLocation).getSystemId());
                                } else {
                                    // Unknown type of cell signal.
                                    isValid = false;
                                }

                                if (isValid) {
                                    // Add the JSON info for the current cell.
                                    JSONObject cellJSONObject = new JSONObject();
                                    cellJSONObject.put("type", type);
                                    cellJSONObject.put("identity", identityJSONObject);
                                    cellJSONArray.put(cellJSONObject);
                                }
                            }
                        }

                        // Add the cells array to the main JSON object.
                        radioJSONObject.put("cells", cellJSONArray);
                    }
                }

                // Get the ISO country code equivalent of the MCC (Mobile Country Code) of the
                // current registered operator, or nearby cell information if not registered.
                radioJSONObject.put("networkCountryIso", mTelephonyManager.getNetworkCountryIso());
            } catch (SecurityException | JSONException e) {
                e.printStackTrace();
            }
        }

        return radioJSONObject;
    }
}
