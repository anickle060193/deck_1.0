package com.adamnickle.deck;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import de.keyboardsurfer.android.widget.crouton.Crouton;


public class DeckApplication extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH )
        {
            registerActivityLifecycleCallbacks( new ActivityLifecycleCallbacks()
            {
                @Override
                public void onActivityCreated( Activity activity, Bundle bundle )
                {

                }

                @Override
                public void onActivityStarted( Activity activity )
                {

                }

                @Override
                public void onActivityResumed( Activity activity )
                {

                }

                @Override
                public void onActivityPaused( Activity activity )
                {
                    Crouton.clearCroutonsForActivity( activity );
                }

                @Override
                public void onActivityStopped( Activity activity )
                {

                }

                @Override
                public void onActivitySaveInstanceState( Activity activity, Bundle bundle )
                {

                }

                @Override
                public void onActivityDestroyed( Activity activity )
                {

                }
            } );
        }
    }
}
