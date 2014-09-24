package com.adamnickle.deck.Interfaces;


public interface ConnectionListener
{
    public void onMessageReceive( String senderID, int bytes, byte[] data );
    public void onDeviceConnect( String deviceID, String deviceName );
    public void onNotification( String notification );
    public void onConnectionStateChange( Connection.State newState );
    public void onConnectionLost( String deviceID );
    public void onConnectionFailed();
}
