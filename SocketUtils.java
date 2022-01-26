package com.partumsoftware.terminus.Classes;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class SocketUtils {
    private static String TAG = "SocketUtils";
    public static String tmpPassword;

    /**
     * To be called at every service solicitation from UI
     * @param context = the application context to be used
     * @param communication = the concerned communication
     */
    public static void trigSocketService(Context context, Communication communication, String action, String command){
        Intent service = new Intent(context, SocketService.class);
        service.setAction(action);
        service.putExtra(Communication.intent, communication.getId());

        if(command != null)
            service.putExtra("command", command);

        Log.i(TAG, "intent sent: " + action);
        context.startService(service);
    }

    /**
     * To be called at every application opening
     */
    public static void resetAsleepConnections(){
        List<Communication> communications = Database.selectAllCommunications();

        if(communications.size() > 0){
            for(Communication communication : communications){
                if(communication.isConnected()){
                    communication.setConnected(false);
                    Database.updateCommunication(communication.getId(), communication);
                    Log.e(TAG, communication.getFullAddress() + " communication status reset");
                }
            }
        }
    }

    static void pingAddress(Communication _communication, Intent intentDiffuser, Context context) {
        Log.i(TAG, "sending ping request to: " + _communication.getUrl());
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(_communication.getUrl());
            if(inetAddress.isReachable(3000)){
                Log.i(TAG, "host reachable");
                intentDiffuser.setAction("reachable");
                intentDiffuser.putExtra(Communication.intent, _communication.getId());
                context.sendBroadcast(intentDiffuser);
            }else{
                String error = "host unreachable";
                Log.e(TAG, error);
                intentDiffuser.setAction("unreachable");
                intentDiffuser.putExtra("error", error);
                intentDiffuser.putExtra(Communication.intent, _communication.getId());
                context.sendBroadcast(intentDiffuser);
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            intentDiffuser.setAction("unreachable");
            intentDiffuser.putExtra("error", e.toString());
            intentDiffuser.putExtra(Communication.intent, _communication.getId());
            context.sendBroadcast(intentDiffuser);
        }
    }

}
