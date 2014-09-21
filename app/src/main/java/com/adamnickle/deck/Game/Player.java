package com.adamnickle.deck.Game;

import com.adamnickle.deck.Connector;

import java.util.ArrayList;


public class Player extends Connector
{
    private final ArrayList< Card > mHand;

    public Player( String deviceID, String name )
    {
        super( deviceID, name );
        mHand = new ArrayList< Card >();
    }

    public boolean hasCard( Card card )
    {
        return mHand.contains( card );
    }

    public void removeCard( Card card )
    {
        mHand.remove( card );
    }

    public void addCard( Card card )
    {
        mHand.add( card );
    }

    public void clearHand()
    {
        mHand.clear();
    }

    public Card[] getCards()
    {
        return mHand.toArray( new Card[ mHand.size() ] );
    }

    @Override
    public String toString()
    {
        return mName;
    }
}