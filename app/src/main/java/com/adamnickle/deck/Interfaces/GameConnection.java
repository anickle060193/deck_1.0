package com.adamnickle.deck.Interfaces;


import com.adamnickle.deck.Game.Card;
import com.adamnickle.deck.Game.GameMessage;
import com.adamnickle.deck.Game.Player;

import java.util.Arrays;

public abstract class GameConnection implements ConnectionListener
{
    public static final String MOCK_SERVER_ADDRESS = "mock_server_address";
    public static final String MOCK_SERVER_NAME = "Server Host";

    protected ConnectionInterfaceFragment mConnection;
    protected GameConnectionListener mListener;

    public GameConnection( ConnectionInterfaceFragment connection, GameConnectionListener listener )
    {
        mConnection = connection;
        mListener = listener;
    }

    public boolean isServer()
    {
        return mConnection.getConnectionType() == ConnectionInterfaceFragment.CONNECTION_TYPE_SERVER;
    }

    public boolean isGameStarted()
    {
        return mConnection.getState() != ConnectionInterfaceFragment.STATE_NONE;
    }

    public String getLocalPlayerID()
    {
        return mConnection.getLocalDeviceID();
    }

    public String getDefaultLocalPlayerName()
    {
        return mConnection.getLocalDeviceName();
    }

    /*******************************************************************
     * ConnectionListener Methods
     *******************************************************************/
    @Override
    public void onMessageReceive( String senderID, int bytes, byte[] allData )
    {
        final byte[] data = Arrays.copyOf( allData, bytes );
        final GameMessage message = GameMessage.deserializeMessage( data );
        final String originalSenderID = message.getOriginalSenderID();
        final String receiverID = message.getReceiverID();
        switch( message.getMessageType() )
        {
            case MESSAGE_NEW_PLAYER:
                final String name = message.getPlayerName();
                final Player player = new Player( originalSenderID, name );
                mListener.onPlayerConnect( player );
                break;

            case MESSAGE_SET_PLAYER_NAME:
                final String newName = message.getPlayerName();
                mListener.onPlayerNameReceive( originalSenderID, newName );
                break;

            case MESSAGE_PLAYER_LEFT:
                mListener.onPlayerDisconnect( originalSenderID );
                break;

            case MESSAGE_CARD:
                final Card card = message.getCard();
                mListener.onCardReceive( originalSenderID, receiverID, card );
                break;

            case MESSAGE_CARDS:
                final Card[] cards = message.getCards();
                mListener.onCardsReceive( originalSenderID, receiverID, cards );
                break;

            case MESSAGE_CARD_REQUEST:
                mListener.onCardRequested( originalSenderID, receiverID ); //TODO Add something about valid cards
                break;

            case MESSAGE_CLEAR_HAND:
                mListener.onClearPlayerHand( originalSenderID, receiverID );
                break;

            case MESSAGE_SET_DEALER:
                final boolean isDealer = message.getIsDealer();
                mListener.onSetDealer( originalSenderID, receiverID, isDealer );
                break;

            case MESSAGE_CURRENT_PLAYERS:
                final Player[] players = message.getCurrentPlayers();
                mListener.onReceiverCurrentPlayers( originalSenderID, receiverID, players );
                break;
        }
    }

    @Override
    public void onNotification( String notification )
    {
        mListener.onNotification( notification );
    }

    @Override
    public void onConnectionStateChange( int newState )
    {
        mListener.onConnectionStateChange( newState );
    }

    @Override
    public void onConnectionFailed()
    {
        mListener.onNotification( "Could not connect." );
    }

    /*******************************************************************
     * GameConnection Required Methods
     *******************************************************************/
    public abstract void startGame();
    public abstract void requestCard( String requesterID, String requesteeID );
    public abstract void sendCard( String senderID, String receiverID, Card card );
    public abstract void sendCards( String senderID, String receiverID, Card[] cards );
    public abstract void clearPlayerHand( String commandingDeviceID, String toBeClearedDeviceID );
    public abstract void setDealer( String setterID, String setteeID, boolean isDealer );
    public abstract void sendPlayerName( String senderID, String receiverID, String name );
}