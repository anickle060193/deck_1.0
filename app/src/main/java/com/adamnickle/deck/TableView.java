package com.adamnickle.deck;


import android.app.Activity;
import android.os.Build;
import android.view.MotionEvent;

import com.adamnickle.deck.Game.Card;

public class TableView extends GameView
{
    private static final float SCALING_FACTOR = 0.5f;

    public TableView( Activity activity )
    {
        super( activity );
        this.setGameGestureListener( mGameGestureListener );

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
        {
            super.setBackground( getResources().getDrawable( R.drawable.table_shadow ) );
        }
        else
        {
            super.setBackgroundDrawable( getResources().getDrawable( R.drawable.table_shadow ) );
        }
    }

    @Override
    protected void setGameBackground( int drawableIndex )
    {
    }

    @Override
    protected CardDrawable createCardDrawable( String cardHolderID, Card card )
    {
        final CardDrawable cardDrawable = new CardDrawable( TableView.this, mListener, cardHolderID, card, SCALING_FACTOR );
        cardDrawable.flipFaceUp();
        return cardDrawable;
    }

    private GameGestureListener mGameGestureListener = new GameGestureListener()
    {
        @Override
        public boolean onDown( MotionEvent event )
        {
            return true;
        }

        @Override
        public boolean onGameDoubleTap( MotionEvent event )
        {
            return true;
        }

        @Override
        public boolean onMove( MotionEvent e1, MotionEvent e2, float dx, float dy )
        {
            return super.onMove( e1, e2, dx, dy );
        }

        @Override
        public boolean onBackgroundFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY )
        {
            return super.onBackgroundFling( e1, e2, velocityX, velocityY );
        }
    };
}