package com.partumsoftware.terminus.Classes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SocketHandler implements Runnable {
    private static String TAG = "SocketHandler";

    private static List<SocketHandler> waitingSockets = new ArrayList<>();
    private static List<SocketHandler> activeSockets = new ArrayList<>();

    private Communication communication;
    private SocketEventHandler socketEventHandler;

    private Context context;
    private Socket socket;
    private Session session;

    private InputStream inputStream;
    private DataOutputStream dataOutputStream;
    private Thread thread;

    /**
     * SocketDefault objects instantiation
     * @param communication = communication to be used
     * @param socketEventHandler = socket interface given from service
     * @param context = context to be used
     * Additional params (added to avoid useless call in constructors):
     *               thread = single thread for all communication process, creating by the start() method from Runnable
     *               inputStream = inputStream for all communication process, to be used for reading incoming data from server
     *               dataOutpustStream = dataOutputStream for all communication process, to be used for sending data to server
     */
    SocketHandler(Communication communication, SocketEventHandler socketEventHandler, Context context){
        this.communication = communication;
        this.socketEventHandler = socketEventHandler;
        this.context = context;
        this.socket = null;
        this.session = null;
    }

    public static List<SocketHandler> getWaitingSockets() {
        return waitingSockets;
    }

    public static void setWaitingSockets(List<SocketHandler> waitingSockets) {
        SocketHandler.waitingSockets = waitingSockets;
    }

    public static List<SocketHandler> getActiveSockets() {
        return activeSockets;
    }

    public static void setActiveSockets(List<SocketHandler> activeSockets) {
        SocketHandler.activeSockets = activeSockets;
    }

    public Communication getCommunication() {
        return this.communication;
    }

    public void setCommunication(Communication communication) {
        this.communication = communication;
    }

    public SocketEventHandler getSocketEventHandler() {
        return this.socketEventHandler;
    }

    public void setSocketEventHandler(SocketEventHandler socketEventHandler) {
        this.socketEventHandler = socketEventHandler;
    }

    public Context getContext() {
        return this.context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public Session getSession() {
        return this.session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public DataOutputStream getDataOutputStream() {
        return dataOutputStream;
    }

    public void setDataOutputStream(DataOutputStream dataOutputStream) {
        this.dataOutputStream = dataOutputStream;
    }

    public Thread getThread() {
        return this.thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    /**
     * body of the living thread
     */
    @Override
    public void run() {
        try{
            this.getSocketEventHandler().loadHandler(this.getCommunication());
            Log.i(TAG, "connecting to " + this.getCommunication().getFullAddress());

            int x;
            int maxResponseCharLength = 8192;
            int timeoutMs = 3000;
            int timeoutIntervalMs = 1000;
            byte[] chars = new byte[maxResponseCharLength];
            String response;

            if(this.getCommunication().getProtocol().toLowerCase().equals(Communication.protocolTelnet) || this.getCommunication().getProtocol().toLowerCase().equals(Communication.protocolSsl)){
                if(this.getCommunication().getProtocol().toLowerCase().equals(Communication.protocolTelnet)){
                    // for Telnet socket instantiations
                    this.setSocket(new Socket(this.getCommunication().getUrl(), this.getCommunication().getPort()));
                }else if(this.getCommunication().getProtocol().toLowerCase().equals(Communication.protocolSsl)){
                    // for ssl, tls socket instantiations
                    TrustManager[] trustAllCerts = { new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                            @SuppressLint("TrustAllX509TrustManager")
                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                            @SuppressLint("TrustAllX509TrustManager")
                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        }
                    };
                    SSLContext sslContext = SSLContext.getInstance("SSL");
                    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                    SocketFactory socketFactory = sslContext.getSocketFactory();
                    this.setSocket(socketFactory.createSocket(communication.getUrl(), communication.getPort()));
                }

                // for the rest of thread
                if(this.getSocket().isConnected()){
                    // this.getSocket().setSoTimeout(timeoutMs);
                    this.setInputStream(this.getSocket().getInputStream());
                    this.setDataOutputStream(new DataOutputStream(this.getSocket().getOutputStream()));

                    this.getSocketEventHandler().didConnect(this.getCommunication());
                    Log.i(TAG, "connected to " + this.getCommunication().getFullAddress());

                    while(this.getSocket().isConnected()){
                        x = getInputStream().read(chars, 0, maxResponseCharLength);
                        while (x != -1) {
                            response = new String(chars, 0, x);
                            Log.i(TAG, this.getCommunication().getFullAddress() + "> " + response);
                            this.getSocketEventHandler().didReceiveData(response, this.getCommunication());
                            x = getInputStream().read(chars, 0, maxResponseCharLength);
                        }
                    }
                }
            }else if(this.getCommunication().getProtocol().toLowerCase().equals(Communication.protocolSsh)){
                // for ssh socket instantiations
                JSch jSch = new JSch();
                this.setSession(jSch.getSession(this.getCommunication().getUsername(), this.getCommunication().getUrl(), this.getCommunication().getPort()));
                if(this.getCommunication().getPassword() == null){
                    this.getSession().setPassword(SocketUtils.tmpPassword);
                    SocketUtils.tmpPassword = null;
                }

                this.getSession().setPassword(this.getCommunication().getPassword());
                this.getSession().setConfig("StrictHostKeyChecking", "no"); // to be changed
                this.getSession().setServerAliveInterval(timeoutIntervalMs);
                this.getSession().connect();

                // for the rest of thread
                if(this.getSession().isConnected()){
                    Channel channel = this.getSession().openChannel("shell");
                    channel.connect();
                    if(channel.isConnected()){
                        this.getSession().setTimeout(timeoutMs);
                        this.setInputStream(channel.getInputStream());
                        this.setDataOutputStream(new DataOutputStream(channel.getOutputStream()));

                        this.getSocketEventHandler().didConnect(this.getCommunication());
                        Log.i(TAG, "connected to " + this.getCommunication().getFullAddress());

                        while(this.getSession().isConnected()){
                            x = getInputStream().read(chars, 0, maxResponseCharLength);
                            while (x != -1) {
                                response = new String(chars, 0, x);
                                Log.i(TAG, this.getCommunication().getFullAddress() + "> " + response);
                                this.getSocketEventHandler().didReceiveData(response, this.getCommunication());
                                x = getInputStream().read(chars, 0, maxResponseCharLength);
                            }
                        }
                    }
                }
            }
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException | JSchException e) {
            Log.e(TAG, e.toString());
            this.getSocketEventHandler().errorHandler(e, this.getCommunication());
        }
    }

    /**
     * start a thread for the whole communication life cycle
     */
    static void start(SocketHandler socketHandler) {
        if (socketHandler.getThread() == null) {
            socketHandler.setThread(new Thread(socketHandler));
            socketHandler.getThread().start();
        }
    }

    /**
     * to be called on each writing attempt from UI
     * @param message = the message to be sent to the server
     * @param dataOutputStream = stream used for message sending
     */
    static void write(String message, DataOutputStream dataOutputStream) {
        try {
            Log.i(TAG, "write(): " + message);
            message = message + "\r";
            dataOutputStream.write(message.getBytes(),0,message.length());
            dataOutputStream.flush();
        } catch (IOException | NullPointerException ex) {
            Log.e(TAG, ex.toString());
        }
    }

    /**
     * close the socket and leave an IOException error that will then close our thread
     */
    static void stop(SocketHandler socketHandler) {
        try{
            if(socketHandler.getSocket() != null)
                socketHandler.getSocket().close();

            if(socketHandler.getSession() != null)
                socketHandler.getSession().disconnect();

            socketHandler.getSocketEventHandler().errorHandler(new IOException(), socketHandler.getCommunication());

        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

    }

    /**
     * to find the actual living object in our dynamic list from JSON communication object
     * @param communication = communication concerned
     * @return = return concerned living object if found, null if not found
     */
    static SocketHandler getSocketHandlerInList(Communication communication, List<SocketHandler> socketHandlerList){
        SocketHandler socketHandler = null;
        if(socketHandlerList != null && socketHandlerList.size() > 0){
            for(SocketHandler _socketHandler : socketHandlerList){
                if(_socketHandler.getCommunication().getId() == communication.getId()){
                    socketHandler = _socketHandler;
                }
            }
        }

        return socketHandler;
    }


}
