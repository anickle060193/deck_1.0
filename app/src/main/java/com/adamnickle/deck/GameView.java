package com.adamnickle.deck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.adamnickle.deck.Game.Card;
import com.adamnickle.deck.Interfaces.GameUiInterface;
import com.adamnickle.deck.Interfaces.GameUiListener;

import java.util.Iterator;
import java.util.LinkedList;

public class GameView extends View implements GameUiInterface
{
    private static final String TAG = "GameView";

    private static final float MINIMUM_VELOCITY = 400.0f;

    private GestureDetectorCompat mDetector;
    private final LinkedList<CardDrawable> mCardDrawables;
    private SparseArray<CardDrawable> mMovingCardDrawables;

    private final Activity mParentActivity;
    private GameUiListener mListener;

    private final Toast mToast;

    public GameView( Activity activity )
    {
        super( activity );
        Log.d( TAG, "___ CONSTRUCTOR ___" );

        mParentActivity = activity;
        mDetector = new GestureDetectorCompat( activity, mGestureListener );
        mCardDrawables = new LinkedList< CardDrawable >();
        mMovingCardDrawables = new SparseArray< CardDrawable >();

        mToast = Toast.makeText( activity, "", Toast.LENGTH_SHORT );

        for( int i = 0; i < 10; i++ )
        {
            mCardDrawables.add( new CardDrawable( this, getResources(), new Card( i ), 100, 100 ) );
        }
    }

    public void setGameUiListener( GameUiListener gameUiListener )
    {
        mListener = gameUiListener;
    }

    @Override
    protected void onAttachedToWindow()
    {
        postDelayed( mUpdateScreen, 10 );
    }

    private Runnable mUpdateScreen = new Runnable()
    {
        @Override
        public void run()
        {
            GameView.this.invalidate();

            postDelayed( this, 10 );
        }
    };

    @Override
    public void onDraw( Canvas canvas )
    {
        synchronized( mCardDrawables )
        {
            Iterator< CardDrawable > cardDrawableIterator = mCardDrawables.descendingIterator();
            CardDrawable cardDrawable;
            while( cardDrawableIterator.hasNext() )
            {
                cardDrawable = cardDrawableIterator.next();
                if( cardDrawable != null )
                {
                    cardDrawable.draw( canvas );
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent( MotionEvent event )
    {
        mDetector.onTouchEvent( event );

        final int action = MotionEventCompat.getActionMasked( event );
        switch( action )
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                switch( action )
                {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        break;
                }
                final int pointerIndex = MotionEventCompat.getActionIndex( event );
                final int pointerId = MotionEventCompat.getPointerId( event, pointerIndex );
                final float x = MotionEventCompat.getX( event, pointerIndex );
                final float y = MotionEventCompat.getY( event, pointerIndex );

                CardDrawable activeCardDrawable = null;
                for( CardDrawable cardDrawable : mCardDrawables )
                {
                    if( cardDrawable != null && !cardDrawable.isHeld() && cardDrawable.contains( (int) x, (int) y ) )
                    {
                        activeCardDrawable = cardDrawable;
                        break;
                    }
                }
                if( activeCardDrawable != null )
                {
                    activeCardDrawable.setIsHeld( true );
                    mMovingCardDrawables.put( pointerId, activeCardDrawable );
                    mCardDrawables.removeFirstOccurrence( activeCardDrawable );
                    mCardDrawables.addFirst( activeCardDrawable );
                }
                break;
            }

            case MotionEvent.ACTION_MOVE:
            {
                for( int i = 0; i < MotionEventCompat.getPointerCount( event ); i++ )
                {
                    final int pointerId = MotionEventCompat.getPointerId( event, i );
                    final float x = MotionEventCompat.getX( event, i );
                    final float y = MotionEventCompat.getY( event, i );

                    CardDrawable cardDrawable = mMovingCardDrawables.get( pointerId );
                    if( cardDrawable != null )
                    {
                        cardDrawable.update( (int) x, (int) y );
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            {
                Log.d( TAG, "--- ACTION CANCEL ---" );
                for( int i = 0; i < mMovingCardDrawables.size(); i++ )
                {
                    mMovingCardDrawables.valueAt( i ).setIsHeld( false );
                }
                mMovingCardDrawables.clear();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            {
                switch( action )
                {
                    case MotionEvent.ACTION_UP:
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                }
                final int pointerIndex = MotionEventCompat.getActionIndex( event );
                final int pointerId = MotionEventCompat.getPointerId( event, pointerIndex );
                final CardDrawable cardDrawable = mMovingCardDrawables.get( pointerId );
                if( cardDrawable != null )
                {
                    cardDrawable.setIsHeld( false );
                    mMovingCardDrawables.remove( pointerId );
                }
                break;
            }
        }

        return true;
    }

    public void onOrientationChange()
    {
        Log.d( TAG, "__ ORIENTATION CHANGE __" );
        for( CardDrawable cardDrawable : mCardDrawables )
        {
            cardDrawable.onOrientationChange();
        }
    }

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener()
    {
        @Override
        public boolean onDown( MotionEvent event )
        {
            return true;
        }

        @Override
        public boolean onFling( MotionEvent event1, MotionEvent event2, float velocityX, float velocityY )
        {
            final float velocity = (float)Math.sqrt( velocityX * velocityX + velocityY * velocityY );
            if( velocity > MINIMUM_VELOCITY )
            {
                final int pointerIndex = MotionEventCompat.getActionIndex( event2 );
                final int pointerId = MotionEventCompat.getPointerId( event2, pointerIndex );
                final CardDrawable cardDrawable = mMovingCardDrawables.get( pointerId );
                if( cardDrawable != null )
                {
                    cardDrawable.setVelocity( velocityX, velocityY );
                }
            }
            return true;
        }

        @Override
        public boolean onDoubleTap( MotionEvent event )
        {
            final int pointerIndex = MotionEventCompat.getActionIndex( event );
            final int pointerId = MotionEventCompat.getPointerId( event, pointerIndex );
            final CardDrawable cardDrawable = mMovingCardDrawables.get( pointerId );
            if( mListener != null && cardDrawable != null )
            {
                if( mListener.onAttemptSendCard( cardDrawable.getCard() ) )
                {
                    mCardDrawables.remove( cardDrawable );
                }
            }

            new AlertDialog.Builder( getContext() )
                    .setTitle( "Pick background" )
                    .setItems( R.array.backgrounds, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick( DialogInterface dialogInterface, int index )
                        {
                            final TypedArray resources = getResources().obtainTypedArray( R.array.background_drawables );
                            final int resource = resources.getResourceId( index, -1 );
                            BitmapDrawable background = (BitmapDrawable) getResources().getDrawable( resource );
                            background.setTileModeXY( Shader.TileMode.REPEAT, Shader.TileMode.REPEAT );
                            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN )
                            {
                                setBackgroundDrawable( background );
                            } else
                            {
                                setBackground( background );
                            }
                        }
                    } )
                    .show();
            return true;
        }
    };

    @Override
    public void addCardDrawable( Card card )
    {
        mCardDrawables.addFirst( new CardDrawable( this, getResources(), card, getWidth() / 2, getHeight() / 2 ) );
    }

    @Override
    public boolean removeCardDrawable( Card card )
    {
        for( CardDrawable cardDrawable : mCardDrawables )
        {
            if( cardDrawable.getCard().equals( card ) )
            {
                mCardDrawables.remove( cardDrawable );
                return true;
            }
        }
        return false;
    }

    @Override
    public void displayNotification( final String notification )
    {
        mParentActivity.runOnUiThread( new Runnable()
        {
            @Override
            public void run()
            {
                mToast.setText( notification );
                mToast.show();
            }
        } );
    }
}