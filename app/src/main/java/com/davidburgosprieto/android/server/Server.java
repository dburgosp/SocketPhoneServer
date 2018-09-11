package com.davidburgosprieto.android.server;

import android.content.Context;
import android.content.res.Resources;
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
    private Resources mResources;

    Server(MainActivity activity) {
        mActivity = activity;
        mResources = mActivity.getBaseContext().getResources();
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
                    Log.e(TAG, mResources.getString(R.string.get_service_state_error) + e);
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
                Log.e(TAG, mResources.getString(R.string.on_destroy_error) + e);
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
                    mMessage += "#" + count + " " + mResources.getString(R.string.from) + " " +
                            socket.getInetAddress() + ":" + socket.getPort() + "\n";

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
                Log.e(TAG, mResources.getString(R.string.get_socket_server_thread_run_error)
                        + " " + e);
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
            String msgReply = mResources.getString(R.string.hello) + " " + cnt;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(getRadio());
                printStream.close();

                mMessage += mResources.getString(R.string.reply) + " " + msgReply + "\n\n";
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mActivity.mMessageTextView.setText(mMessage);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, mResources.getString(
                        R.string.get_socket_server_reply_thread_run_error) + " " + e);
                mMessage += mResources.getString(R.string.wrong) + " " + e.toString() + "\n";
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
            Enumeration<NetworkInterface> enumNetworkInterfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(mResources.getString(R.string.running));
                        stringBuilder.append(" ");
                        stringBuilder.append(inetAddress.getHostAddress());
                        ip = stringBuilder.toString();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, mResources.getString(R.string.get_ip_address_error) + " " + e);
            ip += mResources.getString(R.string.wrong) + " " + e.toString() + "\n";
        }
        return ip;
    }

    private JSONObject getRadio() {
        final JSONObject radioJSONObject = new JSONObject();

        if (mTelephonyManager != null) {
            try {
                // Get data from TelephonyManager.
                radioJSONObject.put(mResources.getString(R.string.json_key_imei),
                        mTelephonyManager.getDeviceId());
                radioJSONObject.put(mResources.getString(R.string.json_key_software_version),
                        mTelephonyManager.getDeviceSoftwareVersion());

                switch (mTelephonyManager.getPhoneType()) {
                    case (TelephonyManager.PHONE_TYPE_CDMA):
                        radioJSONObject.put(mResources.getString(R.string.json_key_phone_type),
                                mResources.getString(R.string.json_value_cdma));
                        break;
                    case (TelephonyManager.PHONE_TYPE_GSM):
                        radioJSONObject.put(mResources.getString(R.string.json_key_phone_type),
                                mResources.getString(R.string.json_value_gsm));
                        break;
                    case (TelephonyManager.PHONE_TYPE_NONE):
                        radioJSONObject.put(mResources.getString(R.string.json_key_phone_type),
                                mResources.getString(R.string.json_value_none));
                        break;
                    case (TelephonyManager.PHONE_TYPE_SIP):
                        radioJSONObject.put(mResources.getString(R.string.json_key_phone_type),
                                mResources.getString(R.string.json_value_sip));
                        break;
                    default:
                        radioJSONObject.put(mResources.getString(R.string.json_key_phone_type),
                                mResources.getString(R.string.json_value_unknown));
                }

                if (mServiceState != null &&
                        mServiceState.getState() != ServiceState.STATE_IN_SERVICE) {
                    // Phone is not in service. Get some parameters from ServiceState.
                    switch (mServiceState.getState()) {
                        case (ServiceState.STATE_EMERGENCY_ONLY):
                            radioJSONObject.put(mResources.getString(R.string.json_key_service_state),
                                    mResources.getString(R.string.json_value_emergency_only));
                            break;
                        case (ServiceState.STATE_OUT_OF_SERVICE):
                            radioJSONObject.put(mResources.getString(R.string.json_key_service_state),
                                    mResources.getString(R.string.json_value_out_of_service));
                            break;
                        case (ServiceState.STATE_POWER_OFF):
                            radioJSONObject.put(mResources.getString(R.string.json_key_service_state),
                                    mResources.getString(R.string.json_value_power_off));
                            break;
                        default:
                            radioJSONObject.put(mResources.getString(R.string.json_key_service_state),
                                    mResources.getString(R.string.json_value_unknown));
                    }
                    radioJSONObject.put(mResources.getString(R.string.json_key_operator_alpha_long),
                            mServiceState.getOperatorAlphaLong());
                    radioJSONObject.put(mResources.getString(R.string.json_key_roaming),
                            mServiceState.getRoaming());
                }

                String simState;
                switch (mTelephonyManager.getSimState()) {
                    case (TelephonyManager.SIM_STATE_ABSENT):
                        simState = mResources.getString(R.string.json_value_absent);
                        break;
                    case (TelephonyManager.SIM_STATE_CARD_IO_ERROR):
                        simState = mResources.getString(R.string.json_value_card_io_error);
                        break;
                    case (TelephonyManager.SIM_STATE_CARD_RESTRICTED):
                        simState = mResources.getString(R.string.json_value_card_restricted);
                        break;
                    case (TelephonyManager.SIM_STATE_NETWORK_LOCKED):
                        simState = mResources.getString(R.string.json_value_network_locked);
                        break;
                    case (TelephonyManager.SIM_STATE_NOT_READY):
                        simState = mResources.getString(R.string.json_value_not_ready);
                        break;
                    case (TelephonyManager.SIM_STATE_PERM_DISABLED):
                        simState = mResources.getString(R.string.json_value_perm_disabled);
                        break;
                    case (TelephonyManager.SIM_STATE_PIN_REQUIRED):
                        simState = mResources.getString(R.string.json_value_pin_required);
                        break;
                    case (TelephonyManager.SIM_STATE_PUK_REQUIRED):
                        simState = mResources.getString(R.string.json_value_puk_required);
                        break;
                    case (TelephonyManager.SIM_STATE_READY):
                        simState = mResources.getString(R.string.json_value_ready);
                        break;
                    case (TelephonyManager.SIM_STATE_UNKNOWN):
                    default:
                        simState = mResources.getString(R.string.json_value_unknown);
                }
                radioJSONObject.put(mResources.getString(R.string.json_key_sim_state), simState);

                if (!simState.equals(mResources.getString(R.string.json_value_absent)) &&
                        !simState.equals(mResources.getString(R.string.json_value_card_io_error)) &&
                        !simState.equals(mResources.getString(R.string.json_value_unknown))) {
                    // Get SIM and network data.
                    radioJSONObject.put(mResources.getString(R.string.json_key_sim_serial_number),
                            mTelephonyManager.getSimSerialNumber());
                    radioJSONObject.put(mResources.getString(R.string.json_key_sim_operator_name),
                            mTelephonyManager.getSimOperatorName());
                    radioJSONObject.put(mResources.getString(R.string.json_key_network_operator_name),
                            mTelephonyManager.getNetworkOperatorName());
                    radioJSONObject.put(mResources.getString(R.string.json_key_line_1_number),
                            mTelephonyManager.getLine1Number());

                    if (simState.equals(mResources.getString(R.string.json_value_ready))) {
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
                                    type = mResources.getString(R.string.json_value_gsm);

                                    // Signal strength.
                                    CellSignalStrengthGsm cellSignalStrength =
                                            ((CellInfoGsm) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put(mResources.getString(R.string.json_key_dbm),
                                            cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityGsm cellIdentity =
                                            ((CellInfoGsm) cellInfo).getCellIdentity();
                                    identityJSONObject.put(mResources.getString(R.string.json_key_cid),
                                            cellIdentity.getCid());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_lac),
                                            cellIdentity.getLac());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mcc_string),
                                            cellIdentity.getMccString());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mnc_string),
                                            cellIdentity.getMncString());

                                } else if (cellInfo instanceof CellInfoCdma) {
                                    // Cell type.
                                    isValid = true;
                                    type = mResources.getString(R.string.json_value_cdma);

                                    // Signal strength.
                                    CellSignalStrengthCdma cellSignalStrength =
                                            ((CellInfoCdma) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put(mResources.getString(R.string.json_key_dbm),
                                            cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityCdma cellIdentity =
                                            ((CellInfoCdma) cellInfo).getCellIdentity();
                                    identityJSONObject.put(mResources.getString(R.string.json_key_cid),
                                            cellIdentity.getBasestationId());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_lac),
                                            cellIdentity.getNetworkId());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mcc),
                                            cellIdentity.getSystemId());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_sid),
                                            cellIdentity.getSystemId());
                                } else if (cellInfo instanceof CellInfoLte) {
                                    // Cell type.
                                    isValid = true;
                                    type = mResources.getString(R.string.json_value_lte);

                                    // Signal strength.
                                    CellSignalStrengthLte cellSignalStrength =
                                            ((CellInfoLte) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put(mResources.getString(R.string.json_key_dbm),
                                            cellSignalStrength.getDbm());
                                    strengthJSONObject.put(
                                            mResources.getString(R.string.json_key_timing_advance),
                                            cellSignalStrength.getTimingAdvance());

                                    // Cell Identity.
                                    CellIdentityLte cellIdentity =
                                            ((CellInfoLte) cellInfo).getCellIdentity();
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mcc_string),
                                            cellIdentity.getMccString());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mnc_string),
                                            cellIdentity.getMncString());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_ci),
                                            cellIdentity.getCi());
                                } else if (cellInfo instanceof CellInfoWcdma) {
                                    // Cell type.
                                    isValid = true;
                                    type = mResources.getString(R.string.json_value_wcdma);

                                    // Signal strength.
                                    CellSignalStrengthWcdma cellSignalStrength =
                                            ((CellInfoWcdma) cellInfo).getCellSignalStrength();
                                    strengthJSONObject.put(mResources.getString(R.string.json_key_dbm),
                                            cellSignalStrength.getDbm());

                                    // Cell Identity.
                                    CellIdentityWcdma cellIdentity =
                                            ((CellInfoWcdma) cellInfo).getCellIdentity();
                                    identityJSONObject.put(mResources.getString(R.string.json_key_lac),
                                            cellIdentity.getLac());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mcc_string),
                                            cellIdentity.getMccString());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_mnc_string),
                                            cellIdentity.getMncString());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_cid),
                                            cellIdentity.getCid());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_psc),
                                            cellIdentity.getPsc());
                                } else {
                                    // Unknown type of cell signal.
                                    isValid = false;
                                }

                                if (isValid) {
                                    // Add the JSON info for the current cell.
                                    JSONObject cellJSONObject = new JSONObject();
                                    cellJSONObject.put(mResources.getString(R.string.json_key_type), type);
                                    cellJSONObject.put(mResources.getString(R.string.json_key_is_registered),
                                            cellInfo.isRegistered());
                                    cellJSONObject.put(mResources.getString(R.string.json_key_strength),
                                            strengthJSONObject);
                                    cellJSONObject.put(mResources.getString(R.string.json_key_identity),
                                            identityJSONObject);
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
                                    type = mResources.getString(R.string.json_value_gsm);

                                    // Cell Identity.
                                    identityJSONObject.put(mResources.getString(R.string.json_key_cid),
                                            ((GsmCellLocation) cellLocation).getCid());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_lac),
                                            ((GsmCellLocation) cellLocation).getLac());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_psc),
                                            ((GsmCellLocation) cellLocation).getPsc());
                                } else if (cellLocation instanceof CdmaCellLocation) {
                                    // Cell type.
                                    isValid = true;
                                    type = mResources.getString(R.string.json_value_cdma);

                                    // Cell Identity.
                                    identityJSONObject.put(mResources.getString(R.string.json_key_network_id),
                                            ((CdmaCellLocation) cellLocation).getNetworkId());
                                    identityJSONObject.put(mResources.getString(R.string.json_key_system_id),
                                            ((CdmaCellLocation) cellLocation).getSystemId());
                                } else {
                                    // Unknown type of cell signal.
                                    isValid = false;
                                }

                                if (isValid) {
                                    // Add the JSON info for the current cell.
                                    JSONObject cellJSONObject = new JSONObject();
                                    cellJSONObject.put(mResources.getString(R.string.json_key_type), type);
                                    cellJSONObject.put(mResources.getString(R.string.json_key_identity),
                                            identityJSONObject);
                                    cellJSONArray.put(cellJSONObject);
                                }
                            }
                        }

                        // Add the cells array to the main JSON object.
                        radioJSONObject.put(mResources.getString(R.string.json_key_cells), cellJSONArray);
                    }
                }

                // Get the ISO country code equivalent of the MCC (Mobile Country Code) of the
                // current registered operator, or nearby cell information if not registered.
                radioJSONObject.put(mResources.getString(R.string.json_key_network_country_iso),
                        mTelephonyManager.getNetworkCountryIso());
            } catch (SecurityException | JSONException e) {
                e.printStackTrace();
            }
        }

        return radioJSONObject;
    }
}
