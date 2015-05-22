/*
Copyright 2015 Yaniv Bokobza

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.wee.boo.territory;

import java.util.Stack;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

/**
 * The activity for the game.  Listens for callbacks from the game engine, and
 * response appropriately, such as bringing up a 'game over' dialog when a ball
 * hits a moving line and there is only one life left.
 */
public class TerritoryActivity extends Activity
        implements TerritoryView.BallEngineCallBack,
        NewGameCallback,
        DialogInterface.OnCancelListener,
        IActivityRequestHandler{

	private static final String TAG = "Territory";
    private static final int NEW_GAME_NUM_BALLS = 1;
    private static final double LEVEL_UP_THRESHOLD = 0.8;
    private static final int COLLISION_VIBRATE_MILLIS = 50;

    private boolean mVibrateOn;
    
    private int mNumBalls = NEW_GAME_NUM_BALLS;
    
    private TerritoryView mBallsView;

    private static final int WELCOME_DIALOG = 20;
    private static final int GAME_OVER_DIALOG = 21;
    private WelcomeDialog mWelcomeDialog;
    private GameOverDialog mGameOverDialog;

    private TextView mLivesLeft;
    private TextView mPercentContained;
    private int mNumLives;
    private Vibrator mVibrator;
    private TextView mLevelInfo;
    private int mNumLivesStart = 5;

    private Toast mCurrentToast;
    
    /** PRIVATE METHODS TO HANDLE ADS */
    public static final int SHOW_INTERSTITIAL = 222;
	private AdView mAdView;
	private InterstitialAd mInterstitialView;
	private AdState adInterstitialState = AdState.NOT_SHOWN_YET;
	private GameState gameState = GameState.GAME_OVER;
	private enum AdState {
		SHOWN_IN_GAMEOVER,
		SHOWN_IN_PAUSE,
		NOT_SHOWN_YET
	}
	private enum GameState {
		GAME_OVER,
		PAUSE,
		RUN
	}
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Turn off the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        RelativeLayout layout = new RelativeLayout(this);
        
        LayoutInflater li = LayoutInflater.from(this);
        LinearLayout mainLayout = (LinearLayout) li.inflate(R.layout.main, null);
        
        mBallsView = (TerritoryView) mainLayout.findViewById(R.id.ballsView);
        mBallsView.setCallback(this);

        mPercentContained = (TextView) mainLayout.findViewById(R.id.percentContained);
        mLevelInfo = (TextView) mainLayout.findViewById(R.id.levelInfo);
        mLivesLeft = (TextView) mainLayout.findViewById(R.id.livesLeft);

        // we'll vibrate when the ball hits the moving line
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        
        // Admob view setup
	    mAdView = new AdView(this);
        mAdView.setAdSize(AdSize.SMART_BANNER);
        mAdView.setAdUnitId( getString(R.string.banner_ad_unit_id) );
	    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
	    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
	    mAdView.setLayoutParams(lp);
	    mAdView.setId(77777);
	    
	    lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT);
	    lp.addRule(RelativeLayout.ABOVE, 77777);
	    mainLayout.setLayoutParams(lp);
	    
	    layout.addView(mainLayout);
	    layout.addView(mAdView);
	    setContentView(layout);
	    
	    mInterstitialView = new InterstitialAd(this);
        mInterstitialView.setAdUnitId( getString(R.string.interstitial_ad_unit_id) );
        requestNewInterstitial();
        
        mInterstitialView.setAdListener(new AdListener() {
        	/** Called when an ad is loaded. */
        	public void onAdLoaded(){
        		Log.d(TAG, "onAdLoaded");
            }
        	
        	/**
			 * Called when an ad is clicked and about to return to the application.
			 */
			@Override
			public void onAdClosed() {
				Log.d(TAG, "onAdClosed");
				requestNewInterstitial();
			}

			/** Called when an ad failed to load. */
			@Override
			public void onAdFailedToLoad(int error) {
				Log.e(TAG, "onAdFailedToLoad error code:"+error);
			}

			/**
			 * Called when an ad is clicked and going to start a new Activity
			 * that will leave the application (e.g. breaking out to the Browser
			 * or Maps application).
			 */
			@Override
			public void onAdLeftApplication() {
				Log.d(TAG, "onAdLeftApplication");
			}

			/**
			 * Called when an Activity is created in front of the app (e.g. an
			 * interstitial is shown, or an ad is clicked and launches a new
			 * Activity).
			 */
			@Override
			public void onAdOpened() {
				Log.d(TAG, "onAdOpened");
			}
        });
        
        showAd();
    }

    /** {@inheritDoc} */
    public void onEngineReady(BallEngine ballEngine) {
        // display 10 balls bouncing around for visual effect
        ballEngine.reset(SystemClock.elapsedRealtime(), 10);
        mBallsView.setMode(TerritoryView.Mode.Bouncing);

        // show the welcome dialog
        showDialog(WELCOME_DIALOG);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == WELCOME_DIALOG) {
        	gameState = GameState.RUN;
            mWelcomeDialog = new WelcomeDialog(this, this);
            mWelcomeDialog.setOnCancelListener(this);
            return mWelcomeDialog;
        } else if (id == GAME_OVER_DIALOG) {
        	gameState = GameState.GAME_OVER;
        	showInterstitialNow();
            mGameOverDialog = new GameOverDialog(this, this);
            mGameOverDialog.setOnCancelListener(this);
            return mGameOverDialog;
        }
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBallsView.setMode(TerritoryView.Mode.PausedByUser);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mVibrateOn = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Preferences.KEY_VIBRATE, true);

        mNumLivesStart = Preferences.getCurrentDifficulty(this).getLivesToStart();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
		getMenuInflater().inflate(R.menu.main, menu);
		return true;    
    }

    /**
     * We pause the game while the menu is open; this remembers what it was
     * so we can restore when the menu closes
     */
    Stack<TerritoryView.Mode> mRestoreMode = new Stack<TerritoryView.Mode>();

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        saveMode();
        mBallsView.setMode(TerritoryView.Mode.Paused);
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
            case R.id.action_new_game:
                cancelToasts();
                onNewGame();
                break;
            case R.id.action_settings:
                final Intent intent = new Intent();
                intent.setClass(this, Preferences.class);
                startActivity(intent);
                break;
        }

        mRestoreMode.pop(); // don't want to restore when an action was taken

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        restoreMode();
    }

    
    private void saveMode() {
        // don't want to restore to a state where user can't resume game.
        final TerritoryView.Mode mode = mBallsView.getMode();
        final TerritoryView.Mode toRestore = (mode == TerritoryView.Mode.Paused) ?
                TerritoryView.Mode.PausedByUser : mode;
        mRestoreMode.push(toRestore);
    }

    private void restoreMode() {
        if (!mRestoreMode.isEmpty()) {
            mBallsView.setMode(mRestoreMode.pop());
        }
    }

    /** {@inheritDoc} */
    public void onBallHitsMovingLine(final BallEngine ballEngine, float x, float y) {
        if (--mNumLives == 0) {
            saveMode();
            mBallsView.setMode(TerritoryView.Mode.Paused);

            // vibrate three times
            if (mVibrateOn) {
                mVibrator.vibrate(
                    new long[]{0l, COLLISION_VIBRATE_MILLIS,
                                   50l, COLLISION_VIBRATE_MILLIS,
                                   50l, COLLISION_VIBRATE_MILLIS},
                        -1);
            }
            showDialog(GAME_OVER_DIALOG);
            gameState = GameState.GAME_OVER;
        	showInterstitialNow();
        } else {
            if (mVibrateOn) {
                mVibrator.vibrate(COLLISION_VIBRATE_MILLIS);
            }
            updateLivesDisplay(mNumLives);
            if (mNumLives <= 1) {
                mBallsView.postDelayed(mOneLifeToastRunnable, 700);
            } else {
                mBallsView.postDelayed(mLivesBlinkRedRunnable, 700);
            }
        }
    }

    private Runnable mOneLifeToastRunnable = new Runnable() {
        public void run() {
            showToast("1 life left!");
        }
    };

    private Runnable mLivesBlinkRedRunnable = new Runnable() {
        public void run() {
            mLivesLeft.setTextColor(Color.RED);
            mLivesLeft.postDelayed(mLivesTextWhiteRunnable, 2000);
        }
    };

    /** {@inheritDoc} */
    public void onAreaChange(final BallEngine ballEngine) {
        final float percentageFilled = ballEngine.getPercentageFilled();
        updatePercentDisplay(percentageFilled);
        if (percentageFilled > LEVEL_UP_THRESHOLD) {
            levelUp(ballEngine);
        }
    }

    /**
     * Go to the next level
     * @param ballEngine The ball engine.
     */
    private void levelUp(final BallEngine ballEngine) {
        mNumBalls++;

        updatePercentDisplay(0);
        updateLevelDisplay(mNumBalls);
        ballEngine.reset(SystemClock.elapsedRealtime(), mNumBalls);
        mBallsView.setMode(TerritoryView.Mode.Bouncing);
        if (mNumBalls % 4 == 0) {
            mNumLives++;
            updateLivesDisplay(mNumLives);
            showToast("bonus life!");
        }
        if (mNumBalls == 10) {
            showToast("Level 10? You ROCK!");
        } else if (mNumBalls == 15) {
            showToast("BALLS TO THE WALL!");
        }
    }

    private Runnable mLivesTextWhiteRunnable = new Runnable() {

        public void run() {
            mLivesLeft.setTextColor(Color.WHITE);
        }
    };

    private void showToast(String text) {
        cancelToasts();
        mCurrentToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        mCurrentToast.show();
    }

    private void cancelToasts() {
        if (mCurrentToast != null) {
            mCurrentToast.cancel();
            mCurrentToast = null;
        }
    }

    /**
     * Update the header that displays how much of the space has been contained.
     * @param amountFilled The fraction, between 0 and 1, that is filled.
     */
    private void updatePercentDisplay(float amountFilled) {
        final int prettyPercent = (int) (amountFilled *100);
        mPercentContained.setText(
                getString(R.string.percent_contained, prettyPercent));
    }

    /** {@inheritDoc} */
    public void onNewGame() {
    	adInterstitialState = AdState.NOT_SHOWN_YET;
    	gameState = GameState.RUN;
        mNumBalls = NEW_GAME_NUM_BALLS;
        mNumLives = mNumLivesStart;
        updatePercentDisplay(0);
        updateLivesDisplay(mNumLives);
        updateLevelDisplay(mNumBalls);
        mBallsView.getEngine().reset(SystemClock.elapsedRealtime(), mNumBalls);
        mBallsView.setMode(TerritoryView.Mode.Bouncing);
    }
    
    /**
     * Update the header displaying the current level
     */
    private void updateLevelDisplay(int numBalls) {
        mLevelInfo.setText(getString(R.string.level, numBalls));
    }

    /**
     * Update the display showing the number of lives left.
     * @param numLives The number of lives left.
     */
    void updateLivesDisplay(int numLives) {
        String text = (numLives == 1) ?
                getString(R.string.one_life_left) : getString(R.string.lives_left, numLives);
        mLivesLeft.setText(text);
    }

    /** {@inheritDoc} */
    public void onCancel(DialogInterface dialog) {
        if (dialog == mWelcomeDialog || dialog == mGameOverDialog) {
            // user hit back, they're done
            finish();
        }
    }
    
    
    /** PRIVATE METHODS TO HANDLE ADS */
    private void showInterstitialNow() {
        if(mInterstitialView.isLoaded() && isAllowToShowAd(adInterstitialState)) {
        	mInterstitialView.show();
        	adInterstitialState = AdState.SHOWN_IN_GAMEOVER;
        	
        } else {
        	if(!mInterstitialView.isLoaded()) {
        		requestNewInterstitial();
        	}
        	showAd();
        }
	}
    
    private boolean isAllowToShowAd(AdState adState) {
    	boolean isPauseMode = mBallsView.getMode() == TerritoryView.Mode.Paused || mBallsView.getMode() == TerritoryView.Mode.PausedByUser;
    	boolean isGameOverMode = gameState == GameState.GAME_OVER;
    	
    	if(adState.equals(AdState.NOT_SHOWN_YET)) {
    		return true;
    	} else if(adState == AdState.SHOWN_IN_GAMEOVER && isGameOverMode) {
    		return false;	// don't show
    	} else if(adState == AdState.SHOWN_IN_PAUSE && isPauseMode) {
    		return false;	// don't show
    	} else {
    		if(isPauseMode) {
    			adState = AdState.SHOWN_IN_PAUSE;
    		} else if (isGameOverMode) {
    			adState = AdState.SHOWN_IN_GAMEOVER;
    		}
    		return true;
    	}
    }
    
    private void showAd() {
		if(mAdView == null) {
    		mAdView = new AdView(this);
            mAdView.setAdSize(AdSize.SMART_BANNER);
            mAdView.setAdUnitId( getString(R.string.banner_ad_unit_id) );
    	    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
    	    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    	    mAdView.setLayoutParams(lp);
    	}
		mAdView.setVisibility(View.VISIBLE);
		AdRequest adBunnerRequest = new AdRequest.Builder().build();
		mAdView.loadAd(adBunnerRequest);
	}
    private void requestNewInterstitial() {
    	AdRequest adInterstitialRequest = new AdRequest.Builder().build();
        mInterstitialView.loadAd(adInterstitialRequest);	// only for loading the interstitial - not showing right away..
    }
    
	@Override
	public void showInterstitialGW() {
		handler.sendEmptyMessage(SHOW_INTERSTITIAL);
	}
	@SuppressLint("HandlerLeak")
	protected Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SHOW_INTERSTITIAL:
                {
                	showInterstitialNow();
                    break;
                }
            }
        }
    };

    
	
}
