package com.adamnickle.deck;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.adamnickle.deck.Interfaces.ConnectionFragment;
import com.adamnickle.deck.Interfaces.ConnectionListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import de.keyboardsurfer.android.widget.crouton.Style;
import ru.noties.debug.Debug;


public class BluetoothConnectionFragment extends ConnectionFragment<BluetoothDevice>
{
    private static final UUID MY_UUID = UUID.fromString( "e40042a0-240b-11e4-8c21-0800200c9a66" );
    private static final String SERVICE_NAME = "Deck Server";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_FIND_DEVICE = 2;
    private static final int REQUEST_MAKE_DISCOVERABLE = 3;

    private static final int DISCOVERABLE_DURATION = 300;

    private final BluetoothAdapter mBluetoothAdapter;
    private ConnectionListener mListener;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ArrayList< ConnectedThread > mConnectedThreads;
    private State mState;
    private ConnectionType mConnectionType;
    private boolean mAskedToDiscoverable;
    private int mOldScanMode;
    private boolean mRetryFindDevice;

    public BluetoothConnectionFragment()
    {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mState = State.NONE;
        this.mConnectionType = ConnectionType.NONE;
        mAskedToDiscoverable = false;
        mOldScanMode = -1;
        mRetryFindDevice = false;

        mConnectedThreads = new ArrayList< ConnectedThread >();
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

        if( mBluetoothAdapter == null )
        {
            activity.setResult( RESULT_BLUETOOTH_NOT_SUPPORTED, new Intent( ConnectionFragment.class.getName() ) );
            activity.finish();
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction( BluetoothAdapter.ACTION_STATE_CHANGED );
        filter.addAction( BluetoothAdapter.ACTION_SCAN_MODE_CHANGED );
        activity.registerReceiver( mReceiver, filter );
    }

    @Override
    public void onDestroy()
    {
        getActivity().unregisterReceiver( mReceiver );
        this.stopConnection();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu( Menu menu, MenuInflater inflater )
    {
        switch( getConnectionType() )
        {
            case CLIENT:
                inflater.inflate( R.menu.connection_client, menu );
                break;

            case SERVER:
                inflater.inflate( R.menu.connection_server, menu );
                break;
        }
    }

    @Override
    public void onPrepareOptionsMenu( Menu menu )
    {
        switch( getConnectionType() )
        {
            case CLIENT:
            {
                switch( mState )
                {
                    case CONNECTED:
                        menu.findItem( R.id.actionLeaveServer ).setVisible( true );
                        break;
                    default:
                        menu.findItem( R.id.actionLeaveServer ).setVisible( false );
                        break;
                }
                break;
            }

            case SERVER:
            {
                switch( mState )
                {
                    case LISTENING:
                        menu.findItem( R.id.actionFinishConnecting ).setVisible( false );
                        menu.findItem( R.id.actionRestartConnecting ).setVisible( false );
                        break;
                    case CONNECTED_LISTENING:
                        menu.findItem( R.id.actionFinishConnecting ).setVisible( true );
                        menu.findItem( R.id.actionRestartConnecting ).setVisible( false );
                        break;
                    case CONNECTED:
                        menu.findItem( R.id.actionFinishConnecting ).setVisible( false );
                        menu.findItem( R.id.actionRestartConnecting ).setVisible( true );
                        break;
                }
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.actionFinishConnecting:
                finishConnecting();
                return true;

            case R.id.actionRestartConnecting:
                mAskedToDiscoverable = false;
                restartConnection();
                return true;

            case R.id.actionCloseServer:
                stopConnection();
                getActivity().setResult( RESULT_SERVER_CLOSED, new Intent( ConnectionFragment.class.getName() ) );
                getActivity().finish();
                return true;

            case R.id.actionLeaveServer:
                stopConnection();
                getActivity().finish();
                return true;

            default:
                return super.onOptionsItemSelected( item );
        }
    }

    @Override
    public void setConnectionListener( ConnectionListener connectionListener )
    {
        mListener = connectionListener;
    }

    @Override
    public synchronized ConnectionType getConnectionType()
    {
        return mConnectionType;
    }

    @Override
    public synchronized void setConnectionType( ConnectionType connectionType )
    {
        mConnectionType = connectionType;
    }

    @Override
    public synchronized State getState()
    {
        return mState;
    }

    private synchronized void setState( State state )
    {
        Debug.d( "setState() %s -> %s", mState, state );
        if( state != mState )
        {
            mState = state;
            if( mListener != null )
            {
                mListener.onConnectionStateChange( mState );
            }
        }
    }

    @Override
    public boolean isConnected()
    {
        return ( mState == State.CONNECTED ) || ( mState == State.CONNECTED_LISTENING );
    }

    @Override
    public String getLocalDeviceID()
    {
        return mBluetoothAdapter.getAddress();
    }

    @Override
    public String getLocalDeviceName()
    {
        return mBluetoothAdapter.getName();
    }

    @Override
    public synchronized void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        Debug.d( "onActivityResult" );

        switch( requestCode )
        {
            case REQUEST_ENABLE_BLUETOOTH:
                if( resultCode == Activity.RESULT_OK )
                {
                    Debug.d( "ENABLE BLUETOOTH - OK" );

                    if( mConnectionType == ConnectionType.SERVER )
                    {
                        startConnection();
                    }
                    else if( mConnectionType == ConnectionType.CLIENT )
                    {
                        if( mState == State.NONE )
                        {
                            findServer();
                        }
                    }
                }
                else
                {
                    Debug.d( "ENABLE BLUETOOTH - CANCEL" );
                    getActivity().setResult( RESULT_BLUETOOTH_NOT_ENABLED, new Intent( ConnectionFragment.class.getName() )  );
                    getActivity().finish();
                }
                break;

            case REQUEST_MAKE_DISCOVERABLE:
                if( resultCode != DISCOVERABLE_DURATION )
                {
                    mListener.onNotification( "Server was not made discoverable. Unknown devices will not be able to connect.", Style.ALERT );
                }
                if( getState() == ConnectionFragment.State.NONE)
                {
                    startConnection();
                }
                else
                {
                    restartConnection();
                }
                break;

            case REQUEST_FIND_DEVICE:
                switch( resultCode )
                {
                    case Activity.RESULT_OK:
                        final String address = data.getStringExtra( EXTRA_DEVICE_ADDRESS );
                        connect( mBluetoothAdapter.getRemoteDevice( address ) );
                        break;

                    case RESULT_BLUETOOTH_NOT_ENABLED:
                        getActivity().setResult( RESULT_BLUETOOTH_NOT_ENABLED, new Intent( ConnectionFragment.class.getName() ) );
                        getActivity().finish();
                        break;

                    case RESULT_BLUETOOTH_DISABLED:
                        getActivity().setResult( RESULT_BLUETOOTH_DISABLED, new Intent( ConnectionFragment.class.getName() ) );
                        getActivity().finish();
                        break;

                    default:
                        getActivity().setResult( RESULT_NOT_CONNECTED_TO_DEVICE, new Intent( ConnectionFragment.class.getName() )  );
                        getActivity().finish();
                        break;
                }
                break;
        }
    }

    private synchronized boolean ensureConnection()
    {
        if( getConnectionType() == ConnectionType.SERVER )
        {
            if( !mAskedToDiscoverable && mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE )
            {
                Debug.d( "enabling Discoverable" );
                mAskedToDiscoverable = true;
                Intent discoverableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE );
                discoverableIntent.putExtra( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION );
                startActivityForResult( discoverableIntent, REQUEST_MAKE_DISCOVERABLE );
                return false;
            }
        }

        if( !mBluetoothAdapter.isEnabled() )
        {
            Debug.d( "enabling Bluetooth" );
            Intent enableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( enableIntent, REQUEST_ENABLE_BLUETOOTH );
            return false;
        }
        return true;
    }

    public synchronized void startConnection()
    {
        Debug.d( "BEGIN startConnection()" );

        if( !ensureConnection() )
        {
            return;
        }

        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        for( ConnectedThread thread : mConnectedThreads )
        {
            thread.cancel();
        }
        mConnectedThreads.clear();

        switch( getConnectionType() )
        {
            case NONE:
            case CLIENT:
                setState( State.NONE );
                break;

            case SERVER:
                setState( State.LISTENING );

                if( mAcceptThread == null )
                {
                    mAcceptThread = new AcceptThread();
                    mAcceptThread.start();
                    mListener.onConnectionStarted();
                }
                break;
        }
    }

    @Override
    public synchronized void restartConnection()
    {
        Debug.d( "BEGIN restartConnection()" );

        if( getConnectionType() == ConnectionType.CLIENT )
        {
            startConnection();
            return;
        }
        else if( getConnectionType() == ConnectionType.SERVER )
        {
            if( !ensureConnection() )
            {
                return;
            }
        }

        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mConnectedThreads.isEmpty() )
        {
            setState( State.LISTENING );
        }
        else
        {
            setState( State.CONNECTED_LISTENING );
        }

        if( mAcceptThread == null )
        {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    @Override
    public synchronized void finishConnecting()
    {
        Debug.d( "BEGIN finishedConnecting" );

        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mAcceptThread != null )
        {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState( State.CONNECTED );
    }

    @Override
    public synchronized void stopConnection()
    {
        Debug.d( "BEGIN stopConnection" );
        setConnectionType( ConnectionType.NONE );

        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        for( ConnectedThread thread : mConnectedThreads )
        {
            if( thread != null )
            {
                thread.cancel();
            }
        }
        mConnectedThreads.clear();

        if( mAcceptThread != null )
        {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState( State.NONE );
    }

    @Override
    public boolean isPlayerID( String ID )
    {
        return BluetoothAdapter.checkBluetoothAddress( ID );
    }

    @Override
    public void findServer()
    {
        Intent intent = new Intent( getActivity(), DeviceListActivity.class );
        intent.putExtra( EXTRA_RETRYING_FIND, mRetryFindDevice );
        startActivityForResult( intent, REQUEST_FIND_DEVICE );
        mRetryFindDevice = false;
    }

    @Override
    public synchronized void connect( BluetoothDevice device )
    {
        Debug.d( "connect to: %s", device );
        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        mConnectThread = new ConnectThread( device );
        mConnectThread.start();

        setConnectionType( ConnectionType.CLIENT );
        setState( State.CONNECTING );
    }

    public synchronized void connected( BluetoothSocket socket, BluetoothDevice device )
    {
        Debug.d( "connected to: %s", device );

        if( mConnectThread != null )
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( getConnectionType() == ConnectionType.CLIENT )
        {
            for( ConnectedThread thread : mConnectedThreads )
            {
                if( thread != null )
                {
                    thread.cancel();
                }
            }
            mConnectedThreads.clear();

            if( mAcceptThread != null )
            {
                mAcceptThread.cancel();
                mAcceptThread = null;
            }

            setState( State.CONNECTED );
            mListener.onConnectionStarted();
        }
        else
        {
            setState( State.CONNECTED_LISTENING );
        }

        ConnectedThread connectedThread = new ConnectedThread( socket );
        mConnectedThreads.add( connectedThread );

        if( mListener != null )
        {
            mListener.onDeviceConnect( connectedThread.getID(), device.getName() );
        }

        connectedThread.start();
    }

    private void write( String deviceID, byte[] out )
    {
        if( isConnected() )
        {
            synchronized( this )
            {
                for( ConnectedThread connectedThread : mConnectedThreads )
                {
                    if( connectedThread.getID().equals( deviceID ) )
                    {
                        connectedThread.write( out );
                    }
                }
            }
        }
    }

    private void connectionFailed()
    {
        switch( getConnectionType() )
        {
            case CLIENT:
                stopConnection();
                mRetryFindDevice = true;
                this.findServer();
                break;

            case SERVER:
                if( mListener != null )
                {
                    mListener.onConnectionFailed();
                }
                restartConnection();
                break;
        }
    }

    private void connectionLost( ConnectedThread connectedThread )
    {
        if( mListener != null )
        {
            mListener.onConnectionLost( connectedThread.getID() );
        }
        mConnectedThreads.remove( connectedThread );

        if( getConnectionType() == ConnectionType.CLIENT )
        {
            stopConnection();
        }
    }

    @Override
    public void sendDataToDevice( String deviceID, byte[] data )
    {
        if( !isConnected() )
        {
            if( mListener != null )
            {
                mListener.onNotification( "Not connected", Style.ALERT );
            }
            return;
        }

        if( data.length > 0 )
        {
            write( deviceID, data );
        }
    }

    private class AcceptThread extends Thread
    {
        private final BluetoothServerSocket mServerSocket;

        public AcceptThread()
        {
            BluetoothServerSocket temp = null;
            try
            {
                temp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord( SERVICE_NAME, MY_UUID );
            }
            catch( IOException io )
            {
                Debug.e( "Socket listen() failed", io );
            }
            mServerSocket = temp;
        }

        @Override
        public void run()
        {
            Debug.d( "BEGIN AcceptThread" );
            setName( "AcceptThread" );

            BluetoothSocket socket;

            while( mState != ConnectionFragment.State.CONNECTED )
            {
                try
                {
                    socket = mServerSocket.accept();
                }
                catch( IOException io )
                {
                    Debug.e( "Accept Thread accept() failed", io );
                    break;
                }

                if( socket != null )
                {
                    synchronized( BluetoothConnectionFragment.this )
                    {
                        switch( mState )
                        {
                            case LISTENING:
                            case CONNECTING:
                            case CONNECTED_LISTENING:
                                connected( socket, socket.getRemoteDevice() );
                                break;

                            case NONE:
                            case CONNECTED:
                                try
                                {
                                    socket.close();
                                }
                                catch( IOException io )
                                {
                                    Debug.e( "Could not close unwanted socket", io );
                                }
                                break;
                        }
                    }
                }
            }
            Debug.d( "END AcceptThread" );
        }

        public void cancel()
        {
            Debug.d( "AcceptThread cancel()" );

            try
            {
                mServerSocket.close();
            }
            catch( IOException io )
            {
                Debug.e( "close() of server failed", io );
            }
        }
    }

    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mSocket;
        private final BluetoothDevice mDevice;

        public ConnectThread( BluetoothDevice device )
        {
            mDevice = device;

            BluetoothSocket temp = null;
            try
            {
                temp = device.createRfcommSocketToServiceRecord( MY_UUID );
            }
            catch( IOException io )
            {
                Debug.e( "ConnectThread create() failed", io );
            }
            mSocket = temp;
        }

        @Override
        public void run()
        {
            Debug.d( "BEGIN ConnectThread" );
            setName( "ConnectThread" );

            mBluetoothAdapter.cancelDiscovery();

            try
            {
                mSocket.connect();
            }
            catch( IOException io )
            {
                try
                {
                    mSocket.close();
                }
                catch( IOException io2 )
                {
                    Debug.e( "unable to close()", io2 );
                }
                connectionFailed();
                return;
            }

            synchronized( BluetoothConnectionFragment.this )
            {
                mConnectThread = null;
            }

            connected( mSocket, mDevice );
        }

        public void cancel()
        {
            try
            {
                mSocket.close();
            }
            catch( IOException io )
            {
                Debug.e( "ConnectThread close() of socket failed", io );
            }
        }
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket mSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;
        private final String mID;

        public ConnectedThread( BluetoothSocket socket )
        {
            Debug.d( "BEGIN create ConnectedThread" );
            mSocket = socket;
            synchronized( BluetoothConnectionFragment.this )
            {
                mID = socket.getRemoteDevice().getAddress();
            }

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }
            catch( IOException io )
            {
                Debug.e( "Failed to get input/output streams", io );
            }
            mInputStream = tmpIn;
            mOutputStream = tmpOut;
        }

        public String getID()
        {
            return mID;
        }

        public void run()
        {
            Debug.d( "BEGIN ConnectedThread" );

            byte[] buffer = new byte[ 8192 ];
            int bytes;

            while( true )
            {
                try
                {
                    bytes = mInputStream.read( buffer );
                    if( bytes > 0 )
                    {
                        if( mListener != null )
                        {
                            mListener.onMessageReceive( mID, bytes, buffer );
                        }
                    }
                }
                catch( IOException io )
                {
                    Debug.e( "failed to read, disconnected", io );
                    connectionLost( this );
                    break;
                }
            }
        }

        public void write( byte[] buffer )
        {
            try
            {
                mOutputStream.write( buffer );
            }
            catch( IOException io )
            {
                Debug.e( "Exception during write", io );
            }
        }

        public void cancel()
        {
            try
            {
                mSocket.close();
            }
            catch( IOException io )
            {
                Debug.e( "close() of connected socket failed", io );
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            final String action = intent.getAction();

            if( BluetoothAdapter.ACTION_STATE_CHANGED.equals( action ) )
            {
                final int state = intent.getIntExtra( BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR );
                switch( state )
                {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        stopConnection();
                        getActivity().setResult( RESULT_BLUETOOTH_DISABLED, new Intent( ConnectionFragment.class.getName() )  );
                        getActivity().finish();
                        break;
                }
            }
            else if( BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals( action ) )
            {
                final int newScanMode = intent.getIntExtra( BluetoothAdapter.EXTRA_SCAN_MODE, -1 );
                if( mOldScanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE && newScanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE )
                {
                    BluetoothConnectionFragment.this.finishConnecting();
                }
                mOldScanMode = newScanMode;
            }
        }
    };
}
