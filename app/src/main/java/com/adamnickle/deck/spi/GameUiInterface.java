package com.adamnickle.deck.spi;

import com.adamnickle.deck.Game.Card;


public interface GameUiInterface
{
    public void addCard( Card card );
    public boolean removeCard( Card card );
    public void displayNotification( String notification );
}
