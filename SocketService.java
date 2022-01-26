package com.partumsoftware.terminus.Classes;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class SocketService extends IntentService {
    private static String TAG = "SocketService";

    private static SocketEventHandler socketEventHandler;
    private static Intent intentDiffuser;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public SocketService() {
        super("SocketService");
    }

    public static SocketEventHandler getSocketEventHandler() {
        return socketEventHandler;
    }

    public static void setSocketEventHandler(SocketEventHandler socketEventHandler) {
        SocketService.socketEventHandler = socketEventHandler;
    }

    public static Intent getIntentDiffuser() {
        return intentDiffuser;
    }

    public static void setIntentDiffuser(Intent intentDiffuser) {
        SocketService.intentDiffuser = intentDiffuser;
    }

    /**
     * to be called on each service solicitation
     * @param intent = intent given by the UI service call
     */
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent != null) {
            Log.i(TAG, "intent received: " + intent.getAction());

            if(getSocketEventHandler() == null)
                initSocketEventHandler();

            if(getIntentDiffuser() == null)
                initIntentDiffuser();

            String action = intent.getAction();
            Communication communication = Database.selectCommunication(intent.getIntExtra(Communication.intent, 0));

            if(action != null && communication != null){
                SocketHandler socketHandler;
                switch(action){
                    case "ping":
                        SocketUtils.pingAddress(communication, getIntentDiffuser(), SocketService.this);
                        break;
                    case "connect":
                        if(!communication.isConnected()){
                            socketHandler = new SocketHandler(communication, getSocketEventHandler(), SocketService.this);
                            SocketHandler.getWaitingSockets().add(socketHandler);
                            SocketHandler.start(socketHandler);
                        }

                        break;
                    case "write":
                        socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getActiveSockets());
                        String command = intent.getStringExtra("command");
                        if(socketHandler != null && command != null){
                            Map.Entry<Long, List<CMessage>> lastEntry = CommunicationManager.getLastNavigableMapEntry(communication);
                            long currentTimestamp = lastEntry.getKey();

                            NavigableMap<Long, List<CMessage>> updatedExchanges = communication.getExchanges();
                            List<CMessage> updatedCMessageList = updatedExchanges.get(currentTimestamp);

                            if(updatedCMessageList != null){
                                CMessage newContent = new CMessage(System.currentTimeMillis(), command, CMessage.originClient, true);
                                updatedCMessageList.add(newContent);
                                updatedExchanges.put(currentTimestamp, updatedCMessageList);
                                communication.setExchanges(updatedExchanges);
                                Database.updateCommunication(communication.getId(), communication);

                                SocketHandler.write(command, socketHandler.getDataOutputStream());
                            }
                        }

                        break;
                    case "disconnect":
                        socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getActiveSockets());
                        if(socketHandler != null)
                            SocketHandler.stop(socketHandler);

                        break;
                }
            }
        }
    }

    /**
     * to be called once at first service query
     */
    public void initSocketEventHandler(){
        setSocketEventHandler(new SocketEventHandler() {
            /**
             * to be called on each loading event
             * @param communication = communication concerned
             */
            @Override
            public void loadHandler(Communication communication) {
                Log.i(TAG, "loadHandler");
                getIntentDiffuser().setAction("loadHandler");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());
                sendBroadcast(getIntentDiffuser());
                Log.i(TAG, "broadcast sent, action = loadHandler");
            }

            /**
             * to be called on each communication event
             * @param communication = communication concerned
             */
            @Override
            public void didConnect(Communication communication) {
                Log.i(TAG, "didConnect");
                SocketHandler socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getWaitingSockets());
                if(socketHandler != null){
                    SocketHandler.getActiveSockets().add(socketHandler);
                    SocketHandler.getWaitingSockets().remove(socketHandler);
                    SocketHandler.write("", socketHandler.getDataOutputStream());
                }

                communication.setConnected(true);
                List<CMessage> cMessageList = new ArrayList<>();
                communication.getExchanges().put(System.currentTimeMillis(), cMessageList);
                Database.updateCommunication(communication.getId(), communication);
                getIntentDiffuser().setAction("didConnect");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());
                sendBroadcast(getIntentDiffuser());
                Log.i(TAG, "broadcast sent, action = didConnect");
            }

            /**
             * to be called on each incoming data from server
             * @param data = incoming data from server
             * @param communication = communication concerned by the deep thread backend in Work Classes
             */
            @Override
            public void didReceiveData(String data, Communication communication) {
                getIntentDiffuser().setAction("didReceiveData");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());

                Map.Entry<Long, List<CMessage>> lastEntry = CommunicationManager.getLastNavigableMapEntry(communication);
                long currentTimestamp = lastEntry.getKey();

                NavigableMap<Long, List<CMessage>> updatedExchanges = communication.getExchanges();
                List<CMessage> updatedCMessageList = updatedExchanges.get(currentTimestamp);

                if(updatedCMessageList != null){
                    CMessage newContent = new CMessage(System.currentTimeMillis(), data, CMessage.originServer, false);
                    updatedCMessageList.add(newContent);
                    updatedExchanges.put(currentTimestamp, updatedCMessageList);
                    communication.setExchanges(updatedExchanges);
                    Database.updateCommunication(communication.getId(), communication);
                    sendBroadcast(getIntentDiffuser());
                    Log.i(TAG, "broadcast sent, action = didReceiveData");
                }
            }

            /**
             * to be called on each error event
             * @param ex = exception to be displayed
             * @param communication = communication concerned
             */
            @Override
            public void errorHandler(Exception ex, Communication communication) {
                Log.i(TAG, "errorHandler");
                SocketHandler socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getWaitingSockets());
                if(socketHandler != null){
                    SocketHandler.getWaitingSockets().remove(socketHandler);
                }else{
                    socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getActiveSockets());
                    if(socketHandler != null)
                        SocketHandler.getActiveSockets().remove(socketHandler);

                }

                getIntentDiffuser().setAction("errorHandler");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());
                getIntentDiffuser().putExtra("error", ex.toString());
                sendBroadcast(getIntentDiffuser());
                Log.i(TAG, "broadcast sent, action = errorHandler");
            }

            /**
             * to be called on each wrong authentication
             * @param communication = communication concerned
             */
            @Override
            public void warningHandler(Communication communication) {
                Log.i(TAG, "warningHandler");
                getIntentDiffuser().setAction("warningHandler");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());
                sendBroadcast(getIntentDiffuser());
                Log.i(TAG, "broadcast sent, action = warningHandler");
            }

            /**
             * to be called on each disconnection event
             * @param ex = exception to be displayed
             * @param communication = communication concerned
             */
            @Override
            public void didDisconnect(Exception ex, Communication communication) {
                Log.i(TAG, "didDisconnect");
                SocketHandler socketHandler = SocketHandler.getSocketHandlerInList(communication, SocketHandler.getWaitingSockets());
                if(socketHandler != null)
                    SocketHandler.getActiveSockets().remove(socketHandler);

                communication.setConnected(false);
                Database.updateCommunication(communication.getId(), communication);
                getIntentDiffuser().setAction("didDisconnect");
                getIntentDiffuser().putExtra(Communication.intent, communication.getId());
                getIntentDiffuser().putExtra("error", ex.toString());
                sendBroadcast(getIntentDiffuser());
                Log.i(TAG, "broadcast sent, action = didDisconnect");
            }

        });
    }

    /**
     * to be called once at first service query
     */
    public void initIntentDiffuser(){
        setIntentDiffuser(new Intent());
    }

}
