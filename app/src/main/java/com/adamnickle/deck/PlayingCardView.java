package com.adamnickle.deck;


import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.adamnickle.deck.Game.Card;
import com.adamnickle.deck.Game.CardCollection;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.Comparator;

public class PlayingCardView extends ImageView
{
    public static final float CARD_HEADER_PERCENTAGE = 0.27f;

    private static final float MINIMUM_VELOCITY = 50.0f;

    private Bitmap mCardBitmap;
    private final Card mCard;
    private String mOwnerID;
    private boolean mFaceUp;
    private boolean mFirstLayout;
    private float mVelocityX;
    private float mVelocityY;
    private long mLastUpdate;
    private float mScale;

    public PlayingCardView( Context context, String ownerID, Card card, final float scale )
    {
        super( context );

        mCard = card;
        mOwnerID = ownerID;
        mFaceUp = false;
        mFirstLayout = true;
        mVelocityX = 0.0f;
        mVelocityY = 0.0f;
        mScale = scale;

        this.setLayoutParams( new CardDisplayLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
        this.setScaleType( ScaleType.CENTER_CROP );

        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    mCardBitmap = Picasso.with( getContext() ).load( mCard.getResource() ).get();
                    if( mFaceUp )
                    {
                        new Handler( Looper.getMainLooper() ).post( new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                PlayingCardView.this.setImageBitmap( mCardBitmap );
                            }
                        } );
                    }
                }
                catch( IOException e )
                {
                    e.printStackTrace();
                }
            }
        }.start();

        Picasso.with( getContext() ).load( CardResources.BLUE_CARD_BACK ).noFade().placeholder( this.getDrawable() ).into( this, new ScaleCallback() );
    }

    public PlayingCardView( Context context, String ownerID, Card card )
    {
        this( context, ownerID, card, 1.0f );
    }

    private class ScaleCallback implements Callback
    {
        @Override
        public void onSuccess()
        {
            if( mScale != 1.0f )
            {
                CardDisplayLayout.LayoutParams lp = (CardDisplayLayout.LayoutParams) PlayingCardView.this.getLayoutParams();
                lp.width = (int) ( PlayingCardView.this.getDrawable().getIntrinsicWidth() * mScale );
                lp.height = (int) ( PlayingCardView.this.getDrawable().getIntrinsicHeight() * mScale );
                PlayingCardView.this.setLayoutParams( lp );
            }
        }

        @Override
        public void onError()
        {

        }
    }

    private Runnable mFlingUpdater = new Runnable()
    {
        @Override
        public void run()
        {
            synchronized( PlayingCardView.this )
            {
                final long now = System.currentTimeMillis();
                final float elapsedTime = now - mLastUpdate;
                final int dx = (int) ( ( mVelocityX * elapsedTime ) / 1000.0f );
                final int dy = (int) ( ( mVelocityY * elapsedTime ) / 1000.0f );
                PlayingCardView.this.offsetLeftAndRight( dx );
                PlayingCardView.this.offsetTopAndBottom( dy );
                CardDisplayLayout.LayoutParams lp = (CardDisplayLayout.LayoutParams) PlayingCardView.this.getLayoutParams();
                lp.Left += dx;
                lp.Top += dy;
                mVelocityX *= 0.99f;
                mVelocityY *= 0.99f;

                final CardDisplayLayout parent = (CardDisplayLayout) PlayingCardView.this.getParent();

                if( parent == null )
                {
                    PlayingCardView.this.stop();
                }

                if( !PlayingCardView.this.isOnScreen() )
                {
                    PlayingCardView.this.stop();
                    parent.childViewOffScreen( PlayingCardView.this );
                }

                final CardDisplayLayout.Side side = PlayingCardView.this.hasHitWall();

                if( side != CardDisplayLayout.Side.NONE && parent.childShouldBounce( PlayingCardView.this, side ) )
                {
                    switch( side )
                    {
                        case LEFT:
                            mVelocityX = -mVelocityX;
                            PlayingCardView.this.setX( 0 );
                            break;

                        case RIGHT:
                            mVelocityX = -mVelocityX;
                            PlayingCardView.this.offsetLeftAndRight( parent.getRight() - getRight() );
                            break;

                        case TOP:
                            mVelocityY = -mVelocityY;
                            PlayingCardView.this.setY( 0 );
                            break;

                        case BOTTOM:
                            mVelocityY = -mVelocityY;
                            PlayingCardView.this.offsetTopAndBottom( parent.getBottom() - getBottom() );
                            break;
                    }
                }


                if( Math.abs( mVelocityX ) < MINIMUM_VELOCITY )
                {
                    mVelocityX = 0.0f;
                }
                if( Math.abs( mVelocityY ) < MINIMUM_VELOCITY )
                {
                    mVelocityY = 0.0f;
                }
                if( mVelocityX != 0.0f && mVelocityY != 0.0f )
                {
                    mLastUpdate = now;
                    PlayingCardView.this.postDelayed( this, 10 );
                }
            }
        }
    };

    public synchronized void onTouched()
    {
        this.stop();
    }

    public synchronized void fling( float xVelocity, float yVelocity )
    {
        mVelocityX = Math.min( xVelocity / 2.0f, ViewConfiguration.get( getContext() ).getScaledMaximumFlingVelocity() );
        mVelocityY = Math.min( yVelocity / 2.0f, ViewConfiguration.get( getContext() ).getScaledMaximumFlingVelocity() );

        mLastUpdate = System.currentTimeMillis();
        this.post( mFlingUpdater );
    }

    public synchronized void stop()
    {
        mVelocityX = 0.0f;
        mVelocityY = 0.0f;
    }

    @Override
    public void setX( float x )
    {
        this.offsetLeftAndRight( (int) ( x - this.getLeft() ) );
        CardDisplayLayout.LayoutParams lp = (CardDisplayLayout.LayoutParams) this.getLayoutParams();
        lp.Left = (int) x;
    }

    @Override
    public void setY( float y )
    {
        this.offsetTopAndBottom( (int) ( y - this.getTop() ) );
        CardDisplayLayout.LayoutParams lp = (CardDisplayLayout.LayoutParams) this.getLayoutParams();
        lp.Top = (int) y;
    }

    @Override
    protected void onLayout( boolean changed, int left, int top, int right, int bottom )
    {
        super.onLayout( changed, left, top, right, bottom );

        if( mFirstLayout )
        {
            mFirstLayout = false;
            reset();
        }
    }

    private synchronized boolean isOnScreen()
    {
        final ViewGroup parent = (ViewGroup) this.getParent();
        if( parent == null )
        {
            return true;
        }
        else if( this.getRight() <= 0 )
        {
            return false;
        }
        else if( this.getLeft() >= parent.getWidth() )
        {
            return false;
        }
        else if( this.getBottom() <= 0 )
        {
            return false;
        }
        else if( this.getTop() >= parent.getHeight() )
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    private CardDisplayLayout.Side hasHitWall()
    {
        final ViewGroup parent = (ViewGroup) this.getParent();
        if( parent == null )
        {
            return CardDisplayLayout.Side.NONE;
        }
        else if( this.getLeft() <= parent.getLeft() )
        {
            return CardDisplayLayout.Side.LEFT;
        }
        else if( this.getTop() <= parent.getTop() )
        {
            return CardDisplayLayout.Side.TOP;
        }
        else if( this.getRight() >= parent.getRight() )
        {
            return CardDisplayLayout.Side.RIGHT;
        }
        else if( this.getBottom() >= parent.getBottom() )
        {
            return CardDisplayLayout.Side.BOTTOM;
        }
        else
        {
            return CardDisplayLayout.Side.NONE;
        }
    }

    public String getOwnerID()
    {
        return mOwnerID;
    }

    public Card getCard()
    {
        return mCard;
    }

    public void flipFaceUp()
    {
        if( !mFaceUp )
        {
            mFaceUp = true;
            this.setImageBitmap( mCardBitmap );
        }
    }

    public synchronized void reset()
    {
        this.stop();

        final ViewGroup parent = (ViewGroup) this.getParent();
        final int width = parent.getWidth();
        final float widthQuarter = width * 0.25f;
        final int height = parent.getHeight();
        final float heightQuarter = height * 0.25f;
        final int randomXOffset = (int) ( Math.random() * widthQuarter - 0.5f * widthQuarter );
        final int randomYOffset = (int) ( Math.random() * heightQuarter - 0.5f * heightQuarter );
        final int newX = (int) ( ( width - this.getWidth() ) / 2.0f + randomXOffset );
        final int newY = (int) ( ( height - this.getHeight() ) / 2.0f + randomYOffset );

        this.setX( newX );
        this.setY( newY );
    }

    public static class PlayingCardViewComparator implements Comparator< PlayingCardView >
    {
        private final Card.CardComparator mCardComparator;

        public PlayingCardViewComparator( CardCollection.SortingType sortType )
        {
            mCardComparator = new Card.CardComparator( sortType );
        }

        @Override
        public int compare( PlayingCardView cardView, PlayingCardView cardView2 )
        {
            return mCardComparator.compare( cardView.getCard(), cardView2.getCard() );
        }
    }
}