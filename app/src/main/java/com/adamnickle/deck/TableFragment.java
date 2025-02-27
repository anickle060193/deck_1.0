package com.adamnickle.deck;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.adamnickle.deck.Game.Card;
import com.adamnickle.deck.Game.CardHolder;
import com.adamnickle.deck.Game.Game;
import com.adamnickle.deck.Game.GameMessage;
import com.adamnickle.deck.Interfaces.ConnectionFragment;
import com.adamnickle.deck.Interfaces.GameConnection;
import com.adamnickle.deck.Interfaces.GameConnectionListener;
import com.adamnickle.deck.Interfaces.GameUiListener;

import java.util.HashMap;

import de.keyboardsurfer.android.widget.crouton.Style;

public class TableFragment extends Fragment implements GameConnectionListener, GameUiListener
{
    public static final String TABLE_ID = "table";
    public static final String TABLE_NAME = "Table";

    public static final String DRAW_PILE_ID_PREFIX = "draw_pile_";
    private static final String DRAW_PILE_NAME_PREFIX = "Draw Pile ";
    private int DRAW_PILE_COUNT = 0;

    public static final String DISCARD_PILE_ID_PREFIX = "discard_pile_";
    private static final String DISCARD_PILE_NAME_PREFIX = "Discard Pile ";
    private int DISCARD_PILE_COUNT = 0;

    private static float FLING_VELOCITY = 400;

    private GameConnection mGameConnection;
    private CardDisplayLayout mTableView;
    private CardHolder mTable;
    private final HashMap< String, CardHolder > mDrawPiles;
    private final HashMap< String, CardHolder > mDiscardPiles;

    private SlidingFrameLayout mSlidingTableLayout;

    public TableFragment()
    {
        mDrawPiles = new HashMap< String, CardHolder >();
        mDiscardPiles = new HashMap< String, CardHolder >();
    }

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setRetainInstance( true );

        FLING_VELOCITY *= getResources().getDisplayMetrics().density;
        mSlidingTableLayout = (SlidingFrameLayout) getActivity().findViewById( R.id.table );
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState )
    {
        if( mTableView == null )
        {
            mTableView = new CardDisplayLayout( getActivity() )
            {
                @Override
                public PlayingCardView createPlayingCardView( String cardHolderID, final Card card )
                {
                    final PlayingCardView playingCardView = new PlayingCardView( getContext(), cardHolderID, card, 0.5f );
                    if( cardHolderID.startsWith( DRAW_PILE_ID_PREFIX ) )
                    {
                        playingCardView.addOnLayoutChangeListener( new OnLayoutChangeListener()
                        {
                            @Override
                            public void onLayoutChange( View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom )
                            {
                                final PlayingCardView cardView = (PlayingCardView) view;
                                final int count = Integer.parseInt( cardView.getOwnerID().substring( DRAW_PILE_ID_PREFIX.length() ) );
                                final int padding = getResources().getDimensionPixelOffset( R.dimen.table_padding );
                                final int x = ( count == 1 ) ? padding : padding + ( padding + right - left ) * ( count - 1 );
                                final int y = mTableView.getTop() + padding;
                                cardView.setX( x );
                                cardView.setY( y );
                            }
                        } );
                    }
                    else
                    {
                        if( cardHolderID.startsWith( DISCARD_PILE_ID_PREFIX ) )
                        {
                            playingCardView.addOnLayoutChangeListener( new OnLayoutChangeListener()
                            {
                                @Override
                                public void onLayoutChange( View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom )
                                {
                                    final PlayingCardView cardView = (PlayingCardView) view;
                                    final int count = Integer.parseInt( cardView.getOwnerID().substring( DISCARD_PILE_ID_PREFIX.length() ) );
                                    final int padding = getResources().getDimensionPixelOffset( R.dimen.table_padding );
                                    final int x = ( count == 1 ) ? padding : padding + ( padding + right - left ) * ( count - 1 );
                                    final int y = mTableView.getBottom() - ( bottom - top ) - padding;
                                    cardView.setX( x );
                                    cardView.setY( y );
                                }
                            } );
                        }

                        playingCardView.flip( true, false );
                    }
                    return playingCardView;
                }

                @Override
                public void onBackgroundFling( MotionEvent event, MotionEvent event2, float velocityX, float velocityY )
                {
                    if( velocityY < -1.0f * FLING_VELOCITY && Math.abs( velocityX ) < FLING_VELOCITY )
                    {
                        mSlidingTableLayout.collapseFrame();
                    }
                }

                @Override
                public void onCardFling( MotionEvent event, MotionEvent event2, float velocityX, float velocityY, PlayingCardView playingCardView )
                {
                    if( !isCardPile( playingCardView.getOwnerID() ) )
                    {
                        super.onCardFling( event, event2, velocityX, velocityY, playingCardView );
                    }
                }

                @Override
                public void onCardScroll( MotionEvent initialDownEvent, MotionEvent event, float deltaX, float deltaY, PlayingCardView playingCardView )
                {
                    if( !isCardPile( playingCardView.getOwnerID() ) )
                    {
                        super.onCardScroll( initialDownEvent, event, deltaX, deltaY, playingCardView );
                        if( playingCardView.getTop() > ( mSlidingTableLayout.getBottom() - mSlidingTableLayout.getPaddingBottom() ) )
                        {
                            TableFragment.this.onAttemptSendCard( playingCardView.getOwnerID(), playingCardView.getCard(), Side.NONE );
                            final MotionEvent up = MotionEvent.obtain( event );
                            up.setAction( MotionEvent.ACTION_UP );
                            this.onTouchEvent( up );
                        }
                    }
                }

                @Override
                public void onCardSingleTap( MotionEvent event, PlayingCardView playingCardView )
                {
                    if( isCardPile( playingCardView.getOwnerID() ) )
                    {
                        TableFragment.this.onAttemptSendCard( playingCardView.getOwnerID(), playingCardView.getCard(), Side.NONE );
                    }
                }

                @Override
                public void onBackgroundDoubleTap( MotionEvent event )
                {
                }
            };
            mTableView.setGameUiListener( this );
            if( mTable != null )
            {
                mTable.setCardHolderListener( mTableView.getCardHolderListener() );
            }
        }
        else
        {
            ( (ViewGroup) mTableView.getParent() ).removeView( mTableView );
        }

        return mTableView;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        mTableView.onViewDestroy();
    }

    @Override
    public void onCreateOptionsMenu( Menu menu, MenuInflater inflater )
    {
        inflater.inflate( R.menu.table, menu );
    }

    private boolean isCardPile( String cardHolderID )
    {
        return cardHolderID.startsWith( DRAW_PILE_ID_PREFIX ) || cardHolderID.startsWith( DISCARD_PILE_ID_PREFIX );
    }

    @Override
    public boolean onAttemptSendCard( String ownerID, Card card, CardDisplayLayout.Side side )
    {
        if( this.canSendCard( ownerID, card ) )
        {
            mGameConnection.sendCard( ownerID, mGameConnection.getLocalPlayerID(), card, ownerID );
            return true;
        }
        else
        {
            return false;
        }
    }

    @Override
    public boolean canSendCard( String ownerID, Card card )
    {
        if( ownerID.equals( mTable.getID() ) )
        {
            return mTable.hasCard( card );
        }
        else if( ownerID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            final CardHolder drawPile = mDrawPiles.get( ownerID );
            return drawPile != null && drawPile.hasCard( card );
        }
        else if( ownerID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            final CardHolder discardPile = mDiscardPiles.get( ownerID );
            return discardPile != null && discardPile.hasCard( card );
        }
        else
        {
            return false;
        }
    }

    @Override
    public void setGameConnection( GameConnection gameConnection )
    {
        mGameConnection = gameConnection;
    }

    @Override
    public boolean canHandleMessage( GameMessage message )
    {
        final String receiverID = message.getReceiverID();
        return receiverID.equals( TABLE_ID )
            || receiverID.startsWith( DRAW_PILE_ID_PREFIX )
            || receiverID.startsWith( DISCARD_PILE_ID_PREFIX );
    }

    @Override
    public void onCardHolderConnect( String ID, String name )
    {

    }

    @Override
    public void onCardHolderNameReceive( String senderID, String newName )
    {

    }

    @Override
    public void onCardHolderDisconnect( String ID )
    {

    }

    @Override
    public void onGameStarted()
    {

    }

    @Override
    public void onServerConnect( String deviceID, String deviceName )
    {
        mTable = new CardHolder( TABLE_ID, TABLE_NAME );
        if( mTableView != null )
        {
            mTable.setCardHolderListener( mTableView.getCardHolderListener() );
        }
        if( mGameConnection.isServer() )
        {
            mGameConnection.sendCardHolderName( TABLE_ID, GameConnection.MOCK_SERVER_ADDRESS, TABLE_NAME );

            //TODO Send all piles at same time
            final Intent gameIntent = getActivity().getIntent();
            final Game game = gameIntent.getParcelableExtra( GameCreatorWizardActivity.EXTRA_GAME );
            if( game != null )
            {
                for( int i = 0; i < game.DrawPiles; i++ )
                {
                    DRAW_PILE_COUNT++;
                    final String cardPileName = ( game.DrawPiles == 1 ) ? DRAW_PILE_NAME_PREFIX : ( DRAW_PILE_NAME_PREFIX + DRAW_PILE_COUNT );
                    final CardHolder drawPile = new CardHolder( DRAW_PILE_ID_PREFIX + DRAW_PILE_COUNT, cardPileName );
                    drawPile.setCardHolderListener( mTableView );
                    mDrawPiles.put( drawPile.getID(), drawPile );
                    if( mGameConnection.isServer() )
                    {
                        mGameConnection.sendCardHolderName( drawPile.getID(), GameConnection.MOCK_SERVER_ADDRESS, drawPile.getName() );
                    }
                }
                for( int i = 0; i < game.DiscardPiles; i++ )
                {
                    DISCARD_PILE_COUNT++;
                    final String cardPileName = ( game.DiscardPiles == 1 ) ? DISCARD_PILE_NAME_PREFIX : ( DISCARD_PILE_NAME_PREFIX + DISCARD_PILE_COUNT );
                    final CardHolder discardPile = new CardHolder( DISCARD_PILE_ID_PREFIX + DISCARD_PILE_COUNT, cardPileName );
                    discardPile.setCardHolderListener( mTableView );
                    mDiscardPiles.put( discardPile.getID(), discardPile );
                    if( mGameConnection.isServer() )
                    {
                        mGameConnection.sendCardHolderName( discardPile.getID(), GameConnection.MOCK_SERVER_ADDRESS, discardPile.getName() );
                    }
                }
            }
        }
    }

    @Override
    public void onServerDisconnect( String deviceID )
    {

    }

    @Override
    public void onNotification( String notification, Style style )
    {

    }

    @Override
    public void onConnectionStateChange( ConnectionFragment.State newState )
    {

    }

    @Override
    public void onCardReceive( String senderID, String receiverID, Card card )
    {
        if( receiverID.equals( TABLE_ID ) )
        {
            mTable.addCard( card );
        }
        else if( receiverID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            mDrawPiles.get( receiverID ).addCard( card );
        }
        else if( receiverID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            mDiscardPiles.get( receiverID ).addCard( card );
        }
    }

    @Override
    public void onCardsReceive( String senderID, String receiverID, Card[] cards )
    {
        if( receiverID.equals( TABLE_ID ) )
        {
            mTable.addCards( cards );
        }
        else if( receiverID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            mDrawPiles.get( receiverID ).addCards( cards );
        }
        else if( receiverID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            mDiscardPiles.get( receiverID ).addCards( cards );
        }
    }

    @Override
    public void onCardRemove( String removerID, String removedID, Card card )
    {
        if( removedID.equals( TABLE_ID ) )
        {
            mTable.removeCard( card );
        }
        else if( removedID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            mDrawPiles.get( removedID ).removeCard( card );
        }
        else if( removedID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            mDiscardPiles.get( removedID ).removeCard( card );
        }
    }

    @Override
    public void onCardsRemove( String removerID, String removedID, Card[] cards )
    {
        if( removedID.equals( TABLE_ID ) )
        {
            mTable.removeCards( cards );
        }
        else if( removedID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            mDrawPiles.get( removedID ).removeCards( cards );
        }
        else if( removedID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            mDiscardPiles.get( removedID ).removeCards( cards );
        }
    }

    @Override
    public void onClearCards( String commanderID, String commandedID )
    {
        if( commandedID.equals( TABLE_ID ) )
        {
            mTable.clearCards();
        }
        else if( commandedID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            mDrawPiles.get( commandedID ).clearCards();
        }
        else if( commandedID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            mDiscardPiles.get( commandedID ).clearCards();
        }
    }

    @Override
    public void onGameOpen( String senderID, String receiverID, Card[] cards )
    {
        if( receiverID.equals( TABLE_ID ) )
        {
            mTable.clearCards();
            mTable.addCards( cards );
        }
        else if( receiverID.startsWith( DRAW_PILE_ID_PREFIX ) )
        {
            final CardHolder drawPile = mDrawPiles.get( receiverID );
            drawPile.clearCards();
            drawPile.addCards( cards );
        }
        else if( receiverID.startsWith( DISCARD_PILE_ID_PREFIX ) )
        {
            final CardHolder discardPile = mDiscardPiles.get( receiverID );
            discardPile.clearCards();
            discardPile.addCards( cards );
        }
    }

    @Override
    public void onReceiveCardHolders( String senderID, String receiverID, CardHolder[] cardHolders )
    {
        for( CardHolder cardHolder : cardHolders )
        {
            final String cardHolderID = cardHolder.getID();
            if( cardHolderID.startsWith( DRAW_PILE_ID_PREFIX ) )
            {
                cardHolder.setCardHolderListener( mTableView.getCardHolderListener() );
                mDrawPiles.put( cardHolderID, cardHolder );
            }
            else if( cardHolderID.startsWith( DISCARD_PILE_ID_PREFIX ) )
            {
                cardHolder.setCardHolderListener( mTableView.getCardHolderListener() );
                mDiscardPiles.put( cardHolderID, cardHolder );
            }
        }
    }
}
