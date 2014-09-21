package com.adamnickle.deck.Game;

import com.adamnickle.deck.CardResources;

import java.security.InvalidParameterException;
import java.util.Comparator;


public class Card
{
    private int mCardNumber;
    private int mSuit;
    private int mRank;

    public Card( int cardNumber )
    {
        if( cardNumber >= Deck.CARD_COUNT || cardNumber < 0 )
        {
            throw new InvalidParameterException( "Card parameter must be a non-negative integer less than " + Integer.toString( Deck.CARD_COUNT ) );
        }

        mCardNumber = cardNumber;
        mSuit = calculateSuit( mCardNumber );
        mRank = calculateRank( mCardNumber );
    }

    public Card( int suit, int rank )
    {
        if( suit >= Deck.SUITS || suit < 0 )
        {
            throw new InvalidParameterException( "Suit parameter must be a non-negative integer less than " + Integer.toString( Deck.SUITS ) );
        }
        if( rank >= Deck.SUITS || rank < 0 )
        {
            throw new InvalidParameterException( "Rank parameter must be a non-negative integer less than " + Integer.toString( Deck.RANKS ) );
        }

        mSuit = suit;
        mRank = rank;
    }

    public static int calculateSuit( int cardNumber )
    {
        return cardNumber / Deck.RANKS;
    }

    public static int calculateRank( int cardNumber )
    {
        return cardNumber % Deck.RANKS;
    }

    public int getCardNumber()
    {
        return mCardNumber;
    }

    public int getRank()
    {
        return mRank;
    }

    public int getSuit()
    {
        return mSuit;
    }

    public int getResource()
    {
        return CardResources.CARD_RESOURCE_BY_CARD_NUMBER[ getCardNumber() ];
    }

    @Override
    public String toString()
    {
        return Deck.RANK_STRINGS[ mRank ] + " of " + Deck.SUIT_STRINGS[ mSuit ];
    }

    @Override
    public boolean equals( Object o )
    {
        if( this == o )
        {
            return true;
        }

        if( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        Card card = (Card) o;

        if( mCardNumber != card.mCardNumber )
        {
            return false;
        }

        if( mRank != card.mRank )
        {
            return false;
        }

        if( mSuit != card.mSuit )
        {
            return false;
        }

        return true;
    }

    public static class CardComparator implements Comparator< Card >
    {
        public final int mCompareType;

        public CardComparator( int compareType )
        {
            mCompareType = compareType;
        }

        private int compareRank( Card card, Card card2 )
        {
            if( card.getRank() < card2.getRank() )
            {
                return -1;
            }
            else if( card.getRank() > card2.getRank() )
            {
                return 1;
            }
            else
            {
                return 0;
            }
        }

        private int compareSuit( Card card, Card card2 )
        {
            if( card.getSuit() < card2.getSuit() )
            {
                return -1;
            }
            else if( card.getSuit() > card2.getSuit() )
            {
                return 1;
            }
            else
            {
                return 0;
            }
        }

        @Override
        public int compare( Card card, Card card2 )
        {
            if( card == null )
            {
                return -1;
            }
            else if( card2 == null )
            {
                return 1;
            }

            if( mCompareType == CardCollection.SORT_BY_RANK )
            {
                int comp = compareRank( card, card2 );
                if( comp != 0 )
                {
                    return comp;
                }
                else
                {
                    return compareSuit( card, card2 );
                }
            }
            else if( mCompareType == CardCollection.SORT_BY_SUIT )
            {
                int comp = compareSuit( card, card2 );
                if( comp != 0 )
                {
                    return comp;
                }
                else
                {
                    return compareRank( card, card2 );
                }
            }
            else
            {
                throw new InvalidParameterException( "Invalid comparison type." );
            }
        }
    }
}