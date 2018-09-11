# SocketPhoneServer
Very simple sockets server for Android. It listens on the fixed port 8080 and serves some information of the local device from the  [TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager) and [ServiceState](https://developer.android.com/reference/android/telephony/ServiceState) classes.

The server info is formatted into a JSON document with the following optional key-value elements:
```
{
  "imei": TelephonyManager.getDeviceId(),
  "softwareVersion": TelephonyManager.getDeviceSoftwareVersion(),
  "phoneType": TelephonyManager.getPhoneType(),
  "serviceState": ServiceState.getState(),
  "operatorAlphaLong": ServiceState.getOperatorAlphaLong(),
  "roaming": ServiceState.getRoaming(),
  "simState": TelephonyManager.getSimState(),
  "simSerialNumber": TelephonyManager.getSimSerialNumber(),
  "simOperatorName": TelephonyManager.getSimOperatorName(),
  "networkOperatorName": TelephonyManager.getNetworkOperatorName(),
  "line1Number": TelephonyManager.getLine1Number(),
  "networkCountryIso": TelephonyManager.getNetworkCountryIso(),
  "cells": [
    {
      "type": GSM, CDMA, LTE or WCDMA,   
      "isRegistered":mTelephonyManager.getAllCellInfo()[n].isRegistered(),      
      "strength": {
        "dbm": mTelephonyManager.getAllCellInfo()[n].getCellSignalStrength.getDbm(),
        "timingAdvance": mTelephonyManager.getAllCellInfo()[n].getCellSignalStrength.getTimingAdvance()
      },
      "identity": {
        "cid": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getCid(),
        "lac": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getLac(),
        "mccString": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getMccString(),
        "mcnString": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getMcnString(),
        "mcc": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getSystemId(),
        "sid": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getSystemId(),
        "ci": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getCi(),
        "psc": mTelephonyManager.getAllCellInfo()[n].getCellIdentity.getPsc(),
        "networkId": mTelephonyManager.getCellLocation()[n].getNetworkId(),
        "systemId": mTelephonyManager.getCellLocation()[n].getSystemId()
      }
    },
    ...
  ]
}
```
