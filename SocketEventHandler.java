package com.partumsoftware.terminus.Classes;

public interface SocketEventHandler {

    void loadHandler(Communication communication);

    void didConnect(Communication communication);

    void didReceiveData(String data, Communication communication);

    void errorHandler(Exception ex, Communication communication);

    void warningHandler(Communication communication);

    void didDisconnect(Exception ex, Communication communication);


}