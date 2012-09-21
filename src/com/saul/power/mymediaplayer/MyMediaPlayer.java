package com.saul.power.mymediaplayer;

import java.io.IOException;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * This is where the magic happens.  This class is in charge of managing the MediaPlayer
 * and the associated player controls.  Feedback is sent to the parent class via the 
 * state change listener.
 * 
 * @author Saul Howard
 *
 */
public class MyMediaPlayer implements OnCompletionListener, OnBufferingUpdateListener,
									  OnPreparedListener, OnVideoSizeChangedListener, SurfaceHolder.Callback,
									  MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {
	
	private static final String TAG = "MyMediaPlayer";

	public static final String STATE_START = "start";
	public static final String STATE_END = "end";
	public static final String STATE_DONE = "done";
	public static final String STATE_ERROR = "error";
	public static final String STATE_RELEASED = "released";
	
	private Activity activity;
	
    private int orgWidth, orgHeight, curPosition, duration = 0;
    
    // used to keep track of when the last action was taken
    // to hide the control panel
	private long lastActionTime = 0L;
    
    private MediaPlayer mMediaPlayer;
    
	private SurfaceView surface;
    private SurfaceHolder holder;
    
	private Animation fadeIn, fadeOut;
	
	private SeekBar timeline, volume;
    private View controlPanel, videoContainer, waitingIndicator;
    private ImageButton fullscreen, playButton, fforward, frewind;
    private TextView waitingText, timeLeft, timePassed;
    private RelativeLayout container;
    private Button done;
	
	// the resource object for the activity
	private Resources resource;
	
	// keeps track of whether the MediaPlayer is paused or not
	private boolean isPaused;
	
	private boolean started;

	// keeps track of whether the MediaPlayer is being ffwd or rwd
	private boolean isFF, isRR;

	private boolean mIsVideoReadyToBePlayed = false;
    private boolean hideControls = false;
    private boolean loopVideo = false;
    private boolean mWaiting = false;
    private boolean fullScreen = false;
    private boolean autoplay = false;
	
    private String path;
    
    private OnMediaPlayerStateChange onMediaPlayerStateChange;
    
	private OnTouchListener touchListener = new OnTouchListener() {
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				
				lastActionTime = SystemClock.elapsedRealtime();
				
				if (controlPanel.getVisibility() == View.VISIBLE)
					hideControls();
				else
					showControls();
				
				return true;
			}
			
			return false;
		}
	};
	
    /**
     * Creates the click listener for the play/pause button
     */
	private View.OnClickListener onMedia = new View.OnClickListener() {
		
		public void onClick(View v) {
			
			if (mMediaPlayer!=null) {
				
				pausePlay();
			}
		}
	};
	
	private SeekBar.OnSeekBarChangeListener timelineListener = new SeekBar.OnSeekBarChangeListener() {
		
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        	
            if (fromUser && mMediaPlayer != null) {
            	
            	lastActionTime = SystemClock.elapsedRealtime();
            	timers(progress);
            	seekTo(progress);
            }
        }

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			
			if (mMediaPlayer != null)
				pausePlay();
			
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			
			if (mMediaPlayer != null)
				pausePlay();
		}
    };
    
    private SeekBar.OnSeekBarChangeListener volumeListener = new SeekBar.OnSeekBarChangeListener() {
		
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        	
            if (fromUser && mMediaPlayer != null) {
            	
            	lastActionTime = SystemClock.elapsedRealtime();
            	
            	// keeps knob visible
            	int knobPosition = keepKnobVisible(volume.getMax(), volume.getWidth(), 25);
            	
            	if (volume.getProgress() < knobPosition) {
            		
            		volume.setProgress(knobPosition);
            		
        		} else if (volume.getProgress() > (volume.getMax() - knobPosition)) {
        			
        			volume.setProgress(volume.getMax() - knobPosition);
        			
        		} else {
        			
        			volume.setProgress(progress);
        		}
            	
            	mMediaPlayer.setVolume((float)seekBar.getProgress()/100, (float)seekBar.getProgress()/100);
            }
        }

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {}
    };
    
    public MyMediaPlayer(Activity activity, RelativeLayout container, String path) {
		
    	this(activity, container, path, null);
    }
    
    public MyMediaPlayer(Activity activity, RelativeLayout container, String path, OnMediaPlayerStateChange onMediaPlayerStateChange) {
		
    	this(activity, container, path, onMediaPlayerStateChange, false, false, false);
    }
	
	public MyMediaPlayer(Activity activity, RelativeLayout container, String path, OnMediaPlayerStateChange onMediaPlayerStateChange, boolean hideControls, boolean loopVideo, boolean autoplay) {
		
		Log.i(TAG, "New MyMediaPlayer " + curPosition);
		
		this.onMediaPlayerStateChange = onMediaPlayerStateChange;
		this.activity = activity;
		this.container = container;
		this.hideControls = hideControls;
		this.loopVideo = loopVideo;
		this.autoplay = autoplay;
		
    	this.path = path;
    	
    	this.curPosition = 0;
    	this.started = false;
    	
        setupAnimations();
        setupView();
        prepMediaPlayer();
	}
	
	public void resetMediaPlayer() {
		
		if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
			mMediaPlayer.pause();
		}
		
		resetPlayer();
	}
	
	private void setupAnimations() {
		
		fadeIn = AnimationUtils.loadAnimation(activity, R.anim.fade_in_fast);
		fadeOut = AnimationUtils.loadAnimation(activity, R.anim.fade_out_fast);
		fadeOut.setAnimationListener(new AnimationListener() {
			
			@Override
			public void onAnimationStart(Animation animation) {}
			
			@Override
			public void onAnimationRepeat(Animation animation) {}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				
				controlPanel.setVisibility(View.GONE);
				done.setVisibility(View.GONE);
			}
		});
	}
    
    private void setupView() {

		container.removeAllViews();
		container.setVisibility(View.VISIBLE);
			
		LayoutInflater inflater = LayoutInflater.from(activity);
		videoContainer = inflater.inflate(R.layout.video_container, container);
    	
        waitingIndicator = videoContainer.findViewById(R.id.waiting_indicator);
        waitingText = (TextView) videoContainer.findViewById(R.id.waiting_text);
		
        // initialize the surface viewer
        surface = (SurfaceView) videoContainer.findViewById(R.id.surface);

        //initialize UI items that need to be manipulated
		controlPanel = videoContainer.findViewById(R.id.bottom_panel);
		timeLeft = (TextView) videoContainer.findViewById(R.id.time_left);
		timePassed = (TextView) videoContainer.findViewById(R.id.time_passed);

		// setup video track bar listener for video seeking functionality
		timeline = (SeekBar) videoContainer.findViewById(R.id.timeline);
		
		// setup the volume change listener to control the volume level
		volume = (SeekBar) videoContainer.findViewById(R.id.volume);
		
		fforward = (ImageButton) videoContainer.findViewById(R.id.forward);
		frewind = (ImageButton) videoContainer.findViewById(R.id.rewind);
		
		// setup the play/pause button
		playButton = (ImageButton) videoContainer.findViewById(R.id.media);
		playButton.setOnClickListener(onMedia);
		
		// setup the done button
		fullscreen = (ImageButton) videoContainer.findViewById(R.id.fullscreen_button);
		done = (Button) videoContainer.findViewById(R.id.done_button);
			
    }
	
	private void prepMediaPlayer() {

        mWaiting = true;

        // set the display to indeterminate until video is ready
		configureView();
		
    	// initialize variables
        isPaused = true;
        isFF = false;
        isRR = false;
        
        resource = activity.getResources();
        
        activity.getWindow().setFormat(PixelFormat.UNKNOWN);
        
        surface.setVisibility(View.VISIBLE);
        surface.setOnTouchListener(touchListener);
        surface.setKeepScreenOn(true);
        
        holder = surface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        container.setOnTouchListener(touchListener);
        
		timeline.setOnSeekBarChangeListener(timelineListener);
		
		volume.setProgress(50);
		volume.setOnSeekBarChangeListener(volumeListener);
		
		fforward.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				fastFwd();
			}
		});
		
		frewind.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				fastRwd();
			}
		});
		
		fullscreen.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {

				lastActionTime = SystemClock.elapsedRealtime();
				toggleFullScreen();
			}
		});
		
		done.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				if (onMediaPlayerStateChange != null)
					onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_DONE, null);
			}
		});
	}
    
	/**
     * When called it checks to verify if the background process has completed.
     * This is indicated by the mWaiting variable.  Once mWaiting is true the
     * main screen and content are rendered
     */
    private void configureView() {
    	
    	Log.i(TAG, "configureView");

        if (mWaiting) {
        	
            // We're waiting, so show the waiting indicator
            waitingIndicator.setVisibility(View.VISIBLE);
            waitingText.setVisibility(View.VISIBLE);
            hideControls();
            
        } else {
        	
            // We're not waiting, so hide the indicator
        	// In rare cases when videos are changed rapidly the waiting indicator may be null.
        	if (waitingIndicator == null)
        		return;
        	
            waitingIndicator.setVisibility(View.GONE);
            waitingText.setVisibility(View.GONE);
            showControls();
        }
    }
	
	private void showControls() {
		
		if (!hideControls && controlPanel.getVisibility() == View.GONE) {
			
			Log.i(TAG, "Showing controls");
			lastActionTime = SystemClock.elapsedRealtime();
			controlPanel.setVisibility(View.VISIBLE);
			controlPanel.startAnimation(fadeIn);
			done.setVisibility(View.VISIBLE);
			done.startAnimation(fadeIn);
		}
	}
	
	/**
	 * Hides the movie controller panel
	 * @param both
	 */
	private void hideControls() {
		
		if (controlPanel.getVisibility() == View.VISIBLE && (!isPaused || hideControls)) {

			Log.i(TAG, "Hiding controls");
			lastActionTime = SystemClock.elapsedRealtime();
			controlPanel.startAnimation(fadeOut);
			done.startAnimation(fadeOut);
		}
	}
	
	private void resetPlayer() {
		
		started = false;
		isPaused = true;
		
		showControls();
		
		playButton.setBackgroundDrawable(resource.getDrawable(R.drawable.play_button_int));
		
		seekTo(0);
	}
	
	/**
	 * Moves to the position passed to by the method
	 * 
	 * @param position
	 */
	private void seekTo(int position) {
		
		if (mMediaPlayer != null && mIsVideoReadyToBePlayed) {
			
	    	lastActionTime = SystemClock.elapsedRealtime();

	    	mMediaPlayer.seekTo(position);
	    	
			startPlayProgressUpdater();
		}
	}
	
	public void pausePlay() {

		if (mMediaPlayer == null)
			return;
		
    	lastActionTime = SystemClock.elapsedRealtime();
    	
		// pauses the MediaPlayer or continues playing if paused
		if (mMediaPlayer.isPlaying()) {
			
			showControls();
			
			playButton.setBackgroundDrawable(resource.getDrawable(R.drawable.play_button_int));
			mMediaPlayer.pause();
			
			isPaused = true;
			isFF = false;
			isRR = false;
			
		} else if (mIsVideoReadyToBePlayed) {
			
			if (!started) {

		        if (onMediaPlayerStateChange != null)
					onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_START, null);
		        
				started = true;
			}
			
			playButton.setBackgroundDrawable(resource.getDrawable(R.drawable.pause_button_int));
			mMediaPlayer.start();
			
			if (curPosition > 0)
				mMediaPlayer.seekTo(curPosition);
			
			startPlayProgressUpdater();
			
			isPaused = false;
			
			hideControls();
		}
	}
    
    /**
     * Puts the MediaPlayer into fast forward
     * 
     * @param view
     */
    private void fastFwd() {
    	
    	if (mMediaPlayer != null) {
    		
    		if (!isFF) {
    			
    			isFF = true;
    			isRR = false;
	    		fastFwdRwd();
	    		
    		} else {
    			
    			isFF = false;
    		}
    	}
    }
    
    /**
     * Puts the MediaPlayer into rewind
     * 
     * @param view
     */
    private void fastRwd() {
    	
    	if (mMediaPlayer != null) {
    		
			if (!isRR) {
				
    			isRR = true;
    			isFF = false;
	    		fastFwdRwd();
	    		
    		} else {
    			
    			isRR = false;
    		}
    	}
    }
    
    /**
     * Starts the MediaPlayer into rewind or fast forward as called
     */
    private void fastFwdRwd () {
    	
    	if (mMediaPlayer != null) {
    		
    		int direction = 1000;
    		
    		if (isRR)
    			direction = -1000;
    		
    		if ((curPosition + direction) < 0 || (curPosition + direction) > duration || (!isFF && !isRR)) {
    			
    			isFF = false;
    			isRR = false;
    			
    		} else {
    			
    			seekTo(curPosition + direction);
    		
	    		Runnable seeker = new Runnable() {
			        public void run() {
			        	fastFwdRwd();
					}
			    };
			    surface.postDelayed(seeker,250);
    		}
    	}
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
    	
        Log.i(TAG, "surfaceChanged called");
    }
    
    /**
     * Called once the surface is destroyed, releases the MediaPlayer
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceholder) {

        mIsVideoReadyToBePlayed = false;
        Log.i(TAG, "surfaceDestroyed called");
    }

    /**
     * Called once the surface is created
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    	
        Log.i(TAG, "surfaceCreated called");

        startMediaPlayerStream();
    }
	
	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		
		int pos = ((int) (((float)percent / 100.f) * (float)timeline.getMax()));
		timeline.setSecondaryProgress(pos);
		startPlayProgressUpdater();
	}

	/**
	 * Called when the media player is finished playing the video.
	 */
	@Override
    public void onCompletion(MediaPlayer mediaPlayer) {
    	
        Log.i(TAG, "onCompletion called for " + mediaPlayer.toString());
        
        started = false;
        
        if (onMediaPlayerStateChange != null)
			onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_END, null);
		
		if (!loopVideo)
			resetPlayer();
    }

    @Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
    	
		Log.i(TAG, "There has been an error with the MediaPlayer: ERROR " + what);
        
		if (onMediaPlayerStateChange != null)
			onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_ERROR, "" + extra);
		
		releaseMediaPlayer();
		
		return true;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		
		Log.i(TAG, "INFO Message: " + what);
		
		return false;
	}

    /**
     * Once the MediaPlayer is prepared this method is called.  The video
     * begins playing and other variables are initialized
     */
    @Override
    public void onPrepared(MediaPlayer mediaplayer) {
        
        mIsVideoReadyToBePlayed = true;
        
		if (mMediaPlayer != null)
			prepareVideoPlayback();
    }

    /**
     * Called when the video size changes
     */
	@Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        
        if (mIsVideoReadyToBePlayed)
			prepareVideoPlayback(width, height);
    }
	
	/**
	 * Prepares to begin playing the video once the MediaPlayer is prepared. Updates
	 * UI elements and adjusts the screen size if necessary.
	 */
	private void prepareVideoPlayback() {

        mWaiting = false;
        
		orgWidth = mMediaPlayer.getVideoWidth();
		orgHeight = mMediaPlayer.getVideoHeight();
		duration = mMediaPlayer.getDuration();
		
        timeline.setMax(duration);
		
		prepareVideoPlayback(orgWidth, orgHeight);
	}
    
	/**
	 * Prepares to begin playing the video once the MediaPlayer is prepared. Updates
	 * UI elements and adjusts the screen size if necessary.
	 * 
	 * @param width The width of the video to be played
	 * @param height The height of the video to be played
	 */
    private void prepareVideoPlayback(int width, int height) {

		int[] size = adjustedVideoSize(width, height);
		
        holder.setFixedSize(size[0], size[1]);

        mMediaPlayer.setVolume((float)volume.getProgress()/100, (float)volume.getProgress()/100);
        mMediaPlayer.setLooping(loopVideo);

        configureView();

		startPlayProgressUpdater();
		
		if (autoplay && isPaused)
			pausePlay();
    }
    
    public void restartMediaPlayer() {
    	releaseMediaPlayer();
    	prepMediaPlayer();
    }

    /**
     * Called when the MediaPlayer is released
     */
    public void releaseMediaPlayer() {

        mIsVideoReadyToBePlayed = false;
    	started = false;
    	
        if (mMediaPlayer != null) {
        	
        	Log.i(TAG, "Media Player RELEASED");
            mMediaPlayer.release();
            mMediaPlayer = null;
            
            if (onMediaPlayerStateChange != null)
				onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_RELEASED, null);
        }
        
        surface.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
        
        hideControls();
        
        mWaiting = false;
        
        configureView();
        
        videoContainer.setVisibility(View.GONE);
    }
    
    /**
     * Starts a streaming MediaPlayer in the case that there is no external storage
     * to download a buffer file.
     */
    private void startMediaPlayerStream() {
    	
    	Log.i(TAG, "startMediaPlayerStreaming");

		mIsVideoReadyToBePlayed = false;
    	isPaused = true;
        
        try {
	            
            // initializes the media player
    		mMediaPlayer = new MediaPlayer();
        	mMediaPlayer.setDataSource(path);
        	mMediaPlayer.setDisplay(holder);
        	mMediaPlayer.setLooping(loopVideo);
            mMediaPlayer.prepareAsync();
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnInfoListener(this);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
        } catch (IOException e) {
        	
        	if (onMediaPlayerStateChange != null)
				onMediaPlayerStateChange.onMediaPlayerStateChange(STATE_ERROR, e.getMessage());
        	
        	releaseMediaPlayer();
        }   
    }
    
    /**
     * Keeps track of the progress of the video file playing and
     * the last time an action was made in order to hide the video
     * control panel
     */
    private void startPlayProgressUpdater() {
    	
    	if (mMediaPlayer != null) {
    		
    		// updates current position
    		curPosition = mMediaPlayer.getCurrentPosition();
			
    		int knobPosition = keepKnobVisible(timeline.getMax(), timeline.getWidth(), 26);
    		
    		// updates the progress bar keeping the knob in view
    		if (curPosition < knobPosition) {
    			
    			timeline.setProgress(knobPosition);
    			
    		} else if (curPosition > (timeline.getMax() - knobPosition)) {
    			
    			timeline.setProgress(timeline.getMax() - knobPosition);
    			
    		} else {
    			
    			timeline.setProgress(curPosition);
    		}
    		
	    	//updates the timers
	    	timers(curPosition);
	    	
	    	// checks to see if the movie control panel needs to be hidden
	    	if (lastActionTime > 0 && SystemClock.elapsedRealtime() - lastActionTime > 5000)
				hideControls();
	    	
	    	// calls every second to update the UI
				
			Runnable notification = new Runnable() {
				
		        public void run() {
		        	
		        	startPlayProgressUpdater();
				}
		    };
		    
		    surface.postDelayed(notification, 100);
    	}
    }
    
    /**
     * Keeps the custom knob visible otherwise it gets chopped off
     * @param max
     * @param width
     * @param knobWidth
     * @return
     */
    private int keepKnobVisible(int max, int width, int knobWidth) {
    	
    	return (int)((double)max * .75 / (double)width * (double)knobWidth / 2.0);
    }
    
    /**
     * In charge of updating the time passed and time left text fields.
     * Converts the milliseconds passed to hour:minute:second format to
     * display on the movie control panel
     * 
     * @param millis the millisecond position of the MediaPlayer
     */
    private void timers(int millis) {
    	
    	// gets the runtime of the video file
    	int end = duration;
    	
    	// converts the total time to hours, minutes and seconds
    	int hours = millis/3600000;
    	int minutes = millis/60000;
    	int seconds = millis/1000;
    	
    	// formats the time accordingly
    	timePassed.setText(String.format("%02d:%02d:%02d",
    		    hours,
    		    minutes - (hours*60),
    		    (seconds) - (minutes*60)
    		));
    	
    	hours = (end - millis)/3600000;
    	minutes = (end - millis)/60000;
    	seconds = (end - millis)/1000;
    	
    	timeLeft.setText("-"+String.format("%02d:%02d:%02d",
    		    hours,
    		    minutes - (hours*60),
    		    (seconds) - (minutes*60)
    		));
    }
    
    /**
     * Toggles the video to full screen mode or the video's normal size
     */
    private void toggleFullScreen() {
    	
    	int[] size = adjustedVideoSize(orgWidth, orgHeight);
    	
    	int width = size[0];
    	int height = size[1];
    	
    	if (!fullScreen) {

    		fullscreen.setImageResource(R.drawable.normal);
    		
    		Display display = activity.getWindowManager().getDefaultDisplay();

        	double ratio = (double)orgWidth / (double)orgHeight;
        	
        	if (useWidth()) {
        		width = display.getWidth();
        		height = (int) (width / ratio);
        	} else {
        		height = display.getHeight();
        		width = (int) (height * ratio);
        	}
        	
    	} else {

    		fullscreen.setImageResource(R.drawable.fullscreen);
    	}
    	
		holder.setFixedSize(width, height);
    	fullScreen = !fullScreen;
    }
    
    /**
     * Determines whether to fill the video to the screen's width
     * 
     * @return whether the screen's width should be used to scale the video
     */
    private boolean useWidth() {
    	
    	Display display = activity.getWindowManager().getDefaultDisplay();
    	
    	double ratio = (double)orgWidth / (double)orgHeight;

    	int width = display.getWidth();
    	int height = (int) (width / ratio);
    	
    	return (height < display.getHeight());
    }
    
    /**
     * If the video is too large for the screen we scale it down to fit
     * the screen.
     * 
     * @param width The original width of the video
     * @param height The original height of the video
     * 
     * @return An int array of width and height
     */
    private int[] adjustedVideoSize(int width, int height) {

    	int[] size = new int[2];
    	size[0] = width;
    	size[1] = height;
    	
    	Display display = activity.getWindowManager().getDefaultDisplay();
    	
    	if (size[0] > display.getWidth() || size[1] > display.getHeight()) {

        	double ratio = (double)size[0] / (double)size[1];
        	
    		if (useWidth()) {
    			size[0] = display.getWidth();
    			size[1] = (int) (size[0] / ratio);
    		} else {
    			size[1] = display.getHeight();
        		size[0] = (int) (size[1] * ratio);
    		}
    	}
    	
    	return size;
    }
	
	public boolean isPaused() {
		return isPaused;
	}

	public void setPaused(boolean isPaused) {
		this.isPaused = isPaused;
	}
		
	// Define our custom Listener interface
	public interface OnMediaPlayerStateChange {
		public abstract void onMediaPlayerStateChange(String state, String message);
	}
}
