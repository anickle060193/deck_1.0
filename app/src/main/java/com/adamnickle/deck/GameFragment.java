package com.adamnickle.deck;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.adamnickle.deck.Game.Card;
import com.adamnickle.deck.Game.CardCollection;
import com.adamnickle.deck.Game.CardHolder;
import com.adamnickle.deck.Game.DeckSettings;
import com.adamnickle.deck.Game.GameMessage;
import com.adamnickle.deck.Game.GameSaveIO;
import com.adamnickle.deck.Interfaces.ConnectionFragment;
import com.adamnickle.deck.Interfaces.GameConnection;
import com.adamnickle.deck.Interfaces.GameConnectionListener;
import com.adamnickle.deck.Interfaces.GameUiListener;
import com.mikepenz.actionitembadge.library.ActionItemBadge;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class GameFragment extends Fragment implements GameConnectionListener, GameUiListener
{
    private static final long LONG_PRESS_VIBRATE = 40L;

    private int mLastOrientation;
    private CardDisplayLayout mCardDisplay;
    private GameConnection mGameConnection;

    private CardHolder mLocalPlayer;
    private HashMap< String, CardHolder > mCardHolders;
    private ArrayList< CardHolder > mPlayers;

    private CardCollection mDeck;
    private boolean mHasToldToStart;

    private SlidingFrameLayout mSlidingTableLayout;
    private final EnumMap< CardDisplayLayout.Side, CardHolder > mSidesCardHolders;

    private Vibrator mVibrator;

    public GameFragment()
    {
        mCardHolders = new HashMap< String, CardHolder >();
        mPlayers = new ArrayList< CardHolder >();
        mDeck = new CardCollection();
        mHasToldToStart = false;
        mSidesCardHolders = new EnumMap< CardDisplayLayout.Side, CardHolder >( CardDisplayLayout.Side.class );
    }

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setRetainInstance( true );
        setHasOptionsMenu( true );
    }

    @Override
    public void onAttach( Activity activity )
    {
        super.onAttach( activity );

        mVibrator = (Vibrator) activity.getSystemService( Context.VIBRATOR_SERVICE );
    }

    @Override
    public void onActivityCreated( @Nullable Bundle savedInstanceState )
    {
        super.onActivityCreated( savedInstanceState );

        mSlidingTableLayout = (SlidingFrameLayout) getActivity().findViewById( R.id.table );
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedStateInstance )
    {
        if( mCardDisplay == null )
        {
            mCardDisplay = new CardDisplayLayout( getActivity() )
            {
                private MotionEvent mLastLongPress;

                @Override
                public void onBackgroundDown( MotionEvent event )
                {
                    if( mSlidingTableLayout != null )
                    {
                        mSlidingTableLayout.collapseFrame();
                    }
                }

                @Override
                public void onBackgroundDoubleTap( MotionEvent event )
                {
                    final String[] backgroundNames = getResources().getStringArray( R.array.backgrounds );

                    final String currentBackground = PreferenceManager
                            .getDefaultSharedPreferences( getContext().getApplicationContext() )
                            .getString( DeckSettings.BACKGROUND, DeckSettings.DEFAULT_BACKGROUND_VALUE );

                    int selectedBackground = 0;
                    for( int i = 0; i < backgroundNames.length; i++ )
                    {
                        if( backgroundNames[ i ].equals( currentBackground ) )
                        {
                            selectedBackground = i;
                            break;
                        }
                    }

                    new AlertDialog.Builder( getContext() )
                            .setTitle( "Pick background" )
                            .setSingleChoiceItems( R.array.backgrounds, selectedBackground, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick( DialogInterface dialogInterface, int index )
                                {
                                    final String backgroundName = backgroundNames[ index ];
                                    final int backgroundResource = DeckSettings.getBackgroundResourceFromString( getResources(), backgroundName );

                                    PreferenceManager
                                            .getDefaultSharedPreferences( getContext().getApplicationContext() )
                                            .edit()
                                            .putString( DeckSettings.BACKGROUND, backgroundName )
                                            .apply();
                                    setBackgroundResource( backgroundResource );
                                    dialogInterface.dismiss();
                                }
                            } )
                            .show();
                }

                @Override
                public void onCardSingleTap( MotionEvent event, PlayingCardView playingCardView )
                {
                    playingCardView.flip();
                }

                @Override
                public void onCardScroll( MotionEvent initialDownEvent, MotionEvent event, float deltaX, float deltaY, PlayingCardView playingCardView )
                {
                    super.onCardScroll( initialDownEvent, event, deltaX, deltaY, playingCardView );

                    if( mSlidingTableLayout.isOpen() && playingCardView.getBottom() < ( mSlidingTableLayout.getBottom() - mSlidingTableLayout.getPaddingBottom() ) )
                    {
                        mGameConnection.sendCard( playingCardView.getOwnerID(), TableFragment.TABLE_ID, playingCardView.getCard(), playingCardView.getOwnerID() );
                        final MotionEvent up = MotionEvent.obtain( event );
                        up.setAction( MotionEvent.ACTION_UP );
                        this.onTouchEvent( up );
                    }
                }

                @Override
                public void onBackgroundLongPress( MotionEvent event )
                {
                    mLastLongPress = event;
                    mVibrator.vibrate( LONG_PRESS_VIBRATE );
                }

                @Override
                public void onBackgroundUp( MotionEvent event )
                {
                    if( mLastLongPress != null
                     && event.getDownTime() == mLastLongPress.getDownTime() )
                    {
                        final int pointerIndex = event.getActionIndex();
                        final float x = event.getX( pointerIndex );
                        final float y = event.getY( pointerIndex );
                        final float edgeDistance = getResources().getDimensionPixelSize( R.dimen.max_edge_distance );

                        if( x < edgeDistance )
                        {
                            pickPlayerForSide( Side.LEFT );
                        }
                        else if( x > ( this.getWidth() - edgeDistance ) )
                        {
                            pickPlayerForSide( Side.RIGHT );
                        }
                        else if( y < edgeDistance )
                        {
                            pickPlayerForSide( Side.TOP );
                        }
                        else if( y > this.getHeight() - edgeDistance )
                        {
                            pickPlayerForSide( Side.BOTTOM );
                        }
                        mLastLongPress = null;
                    }
                }
            };

            inflater.inflate( R.layout.card_display, mCardDisplay, true );

            final String backgroundName = PreferenceManager
                    .getDefaultSharedPreferences( getActivity() )
                    .getString( DeckSettings.BACKGROUND, DeckSettings.DEFAULT_BACKGROUND_VALUE );
            final int backgroundResource = DeckSettings.getBackgroundResourceFromString( getResources(), backgroundName );
            mCardDisplay.setBackgroundResource( backgroundResource );

            mCardDisplay.setGameUiListener( this );

            if( mLocalPlayer != null )
            {
                mLocalPlayer.setCardHolderListener( mCardDisplay.getCardHolderListener() );
            }

            mLastOrientation = getResources().getConfiguration().orientation;
        }
        else
        {
            ( (ViewGroup) mCardDisplay.getParent() ).removeView( mCardDisplay );

            final int newOrientation = getResources().getConfiguration().orientation;
            if( newOrientation != mLastOrientation )
            {
                mCardDisplay.onOrientationChange();
                mLastOrientation = newOrientation;
            }
        }

        return mCardDisplay;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if( !mHasToldToStart && !mGameConnection.isGameStarted() )
        {
            mHasToldToStart = true;
            mGameConnection.startGame();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        Crouton.clearCroutonsForActivity( getActivity() );
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        mCardDisplay.onViewDestroy();
    }

    @Override
    public void onCreateOptionsMenu( Menu menu, MenuInflater inflater )
    {
        inflater.inflate( R.menu.game, menu );

        if( mGameConnection.isServer() )
        {
            inflater.inflate( R.menu.game_server, menu );

            ActionItemBadge
                    .update(
                            this,
                            menu.findItem( R.id.actionDealCards ),
                            getResources().getDrawable( R.drawable.card ),
                            ActionItemBadge.BadgeStyle.BLUE,
                            mDeck.getCardCount()
                    );
        }
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.actionDealCards:
                handleDealCardsClick();
                return true;

            case R.id.actionClearPlayerHands:
                handleClearPlayerHandsClick();
                return true;

            case R.id.actionDealSingleCard:
                handleDealSingleCardClick();
                return true;

            case R.id.shuffleCards:
                mDeck.shuffle();
                getActivity().invalidateOptionsMenu();
                return true;

            case R.id.actionLayoutCards:
                handleLayoutCardsClick();
                return true;

            case R.id.actionSaveGame:
                handleSaveGameClick();
                return true;

            case R.id.actionOpenGame:
                handleOpenGameClick();
                return true;

            case R.id.actionSetSidesPlayers:
                handleSetPlayerSidesClick();
                return true;

            default:
                return super.onOptionsItemSelected( item );
        }
    }

    private void handleSetPlayerSidesClick()
    {
        final String[] sideNames = { "Left", "Top", "Right", "Bottom" };
        DialogHelper
                .createSelectItemDialog( getActivity(), "Assign players to side to assist passing:", sideNames, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick( DialogInterface dialogInterface, int whichSide )
                    {
                        switch( whichSide )
                        {
                            case 0:
                                pickPlayerForSide( CardDisplayLayout.Side.LEFT );
                                break;
                            case 1:
                                pickPlayerForSide( CardDisplayLayout.Side.TOP );
                                break;
                            case 2:
                                pickPlayerForSide( CardDisplayLayout.Side.RIGHT );
                                break;
                            case 3:
                                pickPlayerForSide( CardDisplayLayout.Side.BOTTOM );
                                break;
                        }
                    }
                } )
                .setPositiveButton( "Cancel", null )
                .show();
    }

    private void pickPlayerForSide( final CardDisplayLayout.Side side )
    {
        final String title = "Assign player to " + side.name() + " side:";
        DialogHelper
                .displayCardHolderList( getActivity(), title, mCardHolders.values(), new DialogHelper.CardHolderOnClickListener()
                {
                    @Override
                    public void onClick( DialogInterface dialog, CardHolder cardHolder )
                    {
                        setCardHolderToSide( side, cardHolder );
                    }
                } )
                .setPositiveButton( "Clear Player", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick( DialogInterface dialog, int whichButton )
                    {
                        setCardHolderToSide( side, null );
                    }
                } )
                .setNegativeButton( "Cancel", null )
                .show();
    }

    private void setCardHolderToSide( CardDisplayLayout.Side side, @Nullable CardHolder cardHolder )
    {
        switch( side )
        {
            case LEFT:
                mSidesCardHolders.put( CardDisplayLayout.Side.LEFT, cardHolder );
                mCardDisplay.findViewById( R.id.leftBorder ).setVisibility( cardHolder != null ? View.VISIBLE : View.INVISIBLE );
                break;

            case TOP:
                mSidesCardHolders.put( CardDisplayLayout.Side.TOP, cardHolder );
                mCardDisplay.findViewById( R.id.topBorder ).setVisibility( cardHolder != null ? View.VISIBLE : View.INVISIBLE );
                break;

            case RIGHT:
                mSidesCardHolders.put( CardDisplayLayout.Side.RIGHT, cardHolder );
                mCardDisplay.findViewById( R.id.rightBorder ).setVisibility( cardHolder != null ? View.VISIBLE : View.INVISIBLE );
                break;

            case BOTTOM:
                mSidesCardHolders.put( CardDisplayLayout.Side.BOTTOM, cardHolder );
                mCardDisplay.findViewById( R.id.bottomBorder ).setVisibility( cardHolder != null ? View.VISIBLE : View.INVISIBLE );
                break;
        }
    }

    private CardHolder[] getDealableCardHolders( boolean includeDrawPiles)
    {
        ArrayList< CardHolder > cardHolders = new ArrayList< CardHolder >( mPlayers );
        if( includeDrawPiles )
        {
            for( CardHolder cardHolder : mCardHolders.values() )
            {
                if( cardHolder.getID().startsWith( TableFragment.DRAW_PILE_ID_PREFIX ) )
                {
                    cardHolders.add( cardHolder );
                }
            }
        }
        return cardHolders.toArray( new CardHolder[ cardHolders.size() ] );
    }

    private void handleClearPlayerHandsClick()
    {
        for( CardHolder player : mCardHolders.values() )
        {
            mGameConnection.clearCards( mLocalPlayer.getID(), player.getID() );
        }
        mDeck.resetCards();
        getActivity().invalidateOptionsMenu();
    }

    private void handleDealCardsClick()
    {
        final CardHolder[] players = getDealableCardHolders( false );
        if( mDeck.getCardCount() == 0 )
        {
            DialogHelper.showPopup( getActivity(), "No Cards Left", "There are no cards left to deal.", "OK" );
        }
        else if( mDeck.getCardCount() < players.length )
        {
            DialogHelper.showPopup( getActivity(), "Not Enough Cards Left", "There are not enough cards left to evenly deal to players.", "OK" );
        }
        else
        {
            final int maxCardsPerPlayer = mDeck.getCardCount() / players.length;
            final Integer[] cardsDealAmounts = new Integer[ maxCardsPerPlayer ];
            for( int i = 1; i <= maxCardsPerPlayer; i++ )
            {
                cardsDealAmounts[ i - 1 ] = i;
            }
            DialogHelper.createSelectItemDialog( getActivity(), "Number of cards to deal to each player:", cardsDealAmounts, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick( DialogInterface dialogInterface, int index )
                {
                    int cardsPerPlayer = cardsDealAmounts[ index ];
                    final Card[][] cardsDealt = new Card[ players.length ][ cardsPerPlayer ];
                    for( int i = 0; i < cardsPerPlayer && mDeck.getCardCount() > 0; i++ )
                    {
                        for( int j = 0; j < players.length; j++ )
                        {
                            cardsDealt[ j ][ i ] = mDeck.removeTopCard();
                        }
                    }

                    for( int i = 0; i < cardsDealt.length; i++ )
                    {
                        mGameConnection.sendCards( mLocalPlayer.getID(), players[ i ].getID(), cardsDealt[ i ] );
                    }
                    getActivity().invalidateOptionsMenu();
                }
            } ).show();
        }
    }

    private void handleDealSingleCardClick()
    {
        final CardHolder[] players = getDealableCardHolders( false );
        if( mDeck.getCardCount() == 0 )
        {
            DialogHelper.showPopup( getActivity(), "No Cards Left", "There are no cards left to deal.", "OK" );
        }
        else
        {
            DialogHelper.displayCardHolderList( getActivity(), "Select player to deal card to:", Arrays.asList( players ), new DialogHelper.CardHolderOnClickListener()
            {
                @Override
                public void onClick( DialogInterface dialog, CardHolder cardHolder )
                {
                    mGameConnection.sendCard( GameConnection.MOCK_SERVER_ADDRESS, cardHolder.getID(), mDeck.removeTopCard(), null );
                    getActivity().invalidateOptionsMenu();
                }
            } ).show();
        }
    }

    private void handleLayoutCardsClick()
    {
        if( mCardDisplay.getChildCount() == 0 )
        {
            DialogHelper.showPopup( getActivity(), "No card to layout", "You do not have any cards to layout.", "Close" );
        }
        else
        {
            DialogHelper.createSelectItemDialog( getActivity(), "Select layout:", new String[]{ "By Rank", "By Suit" }, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick( DialogInterface dialogInterface, int i )
                {
                    if( i == 0 )
                    {
                        mCardDisplay.sortCards( mLocalPlayer.getID(), CardCollection.SortingType.SORT_BY_RANK );
                    }
                    else if( i == 1 )
                    {
                        mCardDisplay.sortCards( mLocalPlayer.getID(), CardCollection.SortingType.SORT_BY_SUIT );
                    }
                    mCardDisplay.layoutCards( mLocalPlayer.getID() );
                }
            } ).show();
        }
    }

    private void handleSaveGameClick()
    {
        if( mGameConnection.isServer() )
        {
            DialogHelper.createEditTextDialog( getActivity(), "Enter Deck game save name:", "OK", "Cancel", new DialogHelper.OnEditTextDialogClickListener()
            {
                @Override
                public void onPositiveButtonClick( DialogInterface dialogInterface, String text )
                {
                    if( mGameConnection.saveGame( getActivity().getApplicationContext(), text ) )
                    {
                        DialogHelper.displayNotification( getActivity(), "Game save successful.", Style.CONFIRM );
                    }
                    else
                    {
                        DialogHelper.displayNotification( getActivity(), "Game save not successful.", Style.ALERT );
                    }
                }
            } ).show();
        }
    }

    private void handleOpenGameClick()
    {
        final RecyclerView gameSaveRecyclerView = GameSaveIO.getGameSaveCards( getActivity() );
        if( gameSaveRecyclerView != null )
        {
            final AlertDialog dialog = DialogHelper
                    .createBlankAlertDialog( getActivity(), "Select game save:" )
                    .setPositiveButton( "Close", null )
                    .create();

            ( (GameSaveIO.GameSaveCardAdapter) gameSaveRecyclerView.getAdapter() ).setGameSaveOnClickListener( new GameSaveIO.GameSaveCardAdapter.GameSaveOnClickListener()
            {
                @Override
                public void onGameSaveClick( File gameSaveFile )
                {
                    if( mGameConnection.openGameSave( getActivity(), gameSaveFile ) )
                    {
                        DialogHelper.displayNotification( getActivity(), "Game open successful.", Style.CONFIRM );
                    }else
                    {
                        DialogHelper.displayNotification( getActivity(), "Game open not successful.", Style.ALERT );
                    }
                    dialog.dismiss();
                }
            } );
            gameSaveRecyclerView.getAdapter().registerAdapterDataObserver( new RecyclerView.AdapterDataObserver()
            {
                @Override
                public void onChanged()
                {
                    if( gameSaveRecyclerView.getAdapter().getItemCount() == 0 )
                    {
                        if( dialog.isShowing() )
                        {
                            dialog.dismiss();
                            handleOpenGameClick();
                        }
                    }
                }
            } );
            dialog.setView( gameSaveRecyclerView );
            dialog.show();
        }
        else
        {
            DialogHelper.showPopup( getActivity(), "Select game save:", "There are no game saves to open.", "OK" );
        }
    }

    /*******************************************************************
     * GameUiListener Methods
     *******************************************************************/
    @Override
    public boolean onAttemptSendCard( final String ownerID, final Card card, CardDisplayLayout.Side side )
    {
        if( this.canSendCard( mLocalPlayer.getID(), card ) )
        {
            final CardHolder cardHolder = mSidesCardHolders.get( side );
            if( cardHolder != null )
            {
                mGameConnection.sendCard( ownerID, cardHolder.getID(), card, ownerID );
            }
            else
            {
                DialogHelper.displayCardHolderList( getActivity(), "Select player to send card to:", mCardHolders.values(), new DialogHelper.CardHolderOnClickListener()
                {
                    @Override
                    public void onClick( DialogInterface dialogInterface, CardHolder clickedCardHolder )
                    {
                        if( clickedCardHolder != null )
                        {
                            mGameConnection.sendCard( ownerID, clickedCardHolder.getID(), card, ownerID );
                        }
                        else
                        {
                            mCardDisplay.resetCard( mLocalPlayer.getID(), card );
                        }
                        dialogInterface.dismiss();
                    }
                } ).setOnCancelListener( new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel( DialogInterface dialogInterface )
                    {
                        mCardDisplay.resetCard( mLocalPlayer.getID(), card );
                        dialogInterface.dismiss();
                    }
                } ).show();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canSendCard( String ownerID, Card card )
    {
        CardHolder player = mCardHolders.get( ownerID );
        return mCardHolders.size() > 1 && player != null && player.hasCard( card );
    }

    /*******************************************************************
     * GameConnectionListener Methods
     *******************************************************************/
    @Override
    public void setGameConnection( GameConnection gameConnection )
    {
        mGameConnection = gameConnection;
    }

    @Override
    public boolean canHandleMessage( GameMessage message )
    {
        return true;
    }

    @Override
    public void onCardHolderConnect( String ID, String name )
    {
        final CardHolder cardHolder = new CardHolder( ID, name );
        mCardHolders.put( ID, cardHolder );
        if( mGameConnection.isPlayerID( ID ) )
        {
            mPlayers.add( cardHolder );
            DialogHelper.displayNotification( getActivity(), name + " joined the game.", Style.CONFIRM );
        }
    }

    @Override
    public void onCardHolderNameReceive( String senderID, String newName )
    {
        final CardHolder player = mCardHolders.get( senderID );
        player.setName( newName );
        DialogHelper.displayNotification( getActivity(), player.getName() + " joined the game.", Style.CONFIRM );
    }

    @Override
    public void onCardHolderDisconnect( String ID )
    {
        if( mGameConnection.isGameStarted() )
        {
            CardHolder player = mCardHolders.remove( ID );
            mPlayers.remove( player );
            if( player != null )
            {
                DialogHelper.displayNotification( getActivity(), player.getName() + " left game.", Style.ALERT );
            }
        }
    }

    @Override
    public void onGameStarted()
    {
        mLocalPlayer = new CardHolder( mGameConnection.getLocalPlayerID(), "ME" );
        if( mCardDisplay != null )
        {
            mLocalPlayer.setCardHolderListener( mCardDisplay.getCardHolderListener() );
        }

        mCardHolders.put( mLocalPlayer.getID(), mLocalPlayer );
        mPlayers.add( mLocalPlayer );
    }

    @Override
    public void onServerConnect( String serverID, String serverName )
    {
        if( !mGameConnection.isServer() )
        {
            DialogHelper.displayNotification( getActivity(), "Connected to " + serverName + "'s server", Style.CONFIRM );
        }
        else
        {
            DialogHelper.displayNotification( getActivity(), "Server started", Style.CONFIRM );
        }

        String playerName = PreferenceManager
                .getDefaultSharedPreferences( getActivity().getApplicationContext() )
                .getString( DeckSettings.PLAYER_NAME, mGameConnection.getDefaultLocalPlayerName() );

        mGameConnection.sendCardHolderName( mLocalPlayer.getID(), GameConnection.MOCK_SERVER_ADDRESS, playerName );
    }

    @Override
    public void onServerDisconnect( String serverID )
    {
        Activity activity = getActivity();
        if( activity != null )
        {
            activity.setResult( GameActivity.RESULT_DISCONNECTED_FROM_SERVER, new Intent( GameActivity.class.getName() ) );
            activity.finish();
        }
    }

    @Override
    public void onNotification( String notification, Style style )
    {
        DialogHelper.displayNotification( getActivity(), notification, style );
    }

    @Override
    public void onConnectionStateChange( ConnectionFragment.State newState )
    {
        final Activity activity = getActivity();
        if( activity != null )
        {
            activity.runOnUiThread( new Runnable()
            {
                @Override
                public void run()
                {
                    activity.invalidateOptionsMenu();
                }
            } );
        }
    }

    @Override
    public void onCardReceive( String senderID, String receiverID, Card card )
    {
        if( receiverID.equals( mLocalPlayer.getID() ) )
        {
            mLocalPlayer.addCard( card );
        }
    }

    @Override
    public void onCardsReceive( String senderID, String receiverID, Card[] cards )
    {
        if( receiverID.equals( mLocalPlayer.getID() ) )
        {
            mLocalPlayer.addCards( cards );
        }
    }

    @Override
    public void onCardRemove( String removerID, String removedID, Card card )
    {
        if( removedID.equals( mLocalPlayer.getID() ) )
        {
            mCardHolders.get( removedID ).removeCard( card );
        }
    }

    @Override
    public void onCardsRemove( String removerID, String removedID, Card[] cards )
    {
        if( removedID.equals( mLocalPlayer.getID() ) )
        {
            mCardHolders.get( removedID ).removeCards( cards );
        }
    }

    @Override
    public void onClearCards( String commanderID, String commandedID )
    {
        if( commandedID.equals( mLocalPlayer.getID() ) )
        {
            mLocalPlayer.clearCards();
            String notification;
            if( commanderID.equals( mLocalPlayer.getID() ) )
            {
                notification = "You cleared your hand.";
            }
            else if( commanderID.equals( GameConnection.MOCK_SERVER_ADDRESS ) )
            {
                notification = "The server host cleared your hand.";
            }
            else
            {
                notification = mCardHolders.get( commanderID ).getName() + " cleared your hand.";
            }
            DialogHelper.displayNotification( getActivity(), notification, Style.INFO );
        }
    }

    @Override
    public void onGameOpen( String senderID, String receiverID, Card[] cards )
    {
        if( receiverID.equals( mLocalPlayer.getID() ) )
        {
            mLocalPlayer.clearCards();
            mLocalPlayer.addCards( cards );
        }
    }

    @Override
    public void onReceiveCardHolders( String senderID, String receiverID, CardHolder[] cardHolders )
    {
        for( CardHolder cardHolder : cardHolders )
        {
            this.onCardHolderConnect( cardHolder.getID(), cardHolder.getName() );
        }
    }
}
