package com.adamnickle.deck.Interfaces;


import com.adamnickle.deck.Game.GameMessage;

public interface ConnectionListener
{
    public void onMessageReceive( String senderID, int bytes, byte[] data );
    public void onMessageHandle( GameConnectionListener listener, String originalSenderID, String receiverID, GameMessage message );
    public void onDeviceConnect( String deviceID, String deviceName );
    public void onNotification( String notification );
    public void onConnectionStarted();
    public void onConnectionStateChange( Connection.State newState );
    public void onConnectionLost( String deviceID );
    public void onConnectionFailed();
}