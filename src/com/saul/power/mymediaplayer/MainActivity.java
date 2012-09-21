package com.saul.power.mymediaplayer;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;

import com.saul.power.mymediaplayer.MyMediaPlayer.OnMediaPlayerStateChange;
import com.saul.power.mymediaplayer.utils.CacheUtils;
import com.saul.power.mymediaplayer.utils.DialogUtils;

public class MainActivity extends Activity {

	private final String TAG = "MainActivity";

	private RelativeLayout videoLayout;
	private String path;
	private MyMediaPlayer myMediaPlayer;
	protected boolean downloading = false;
	
	private AsyncTask<Void, Void, Boolean> downloader;
	
	private OnMediaPlayerStateChange mediaListener = new OnMediaPlayerStateChange() {
		
		@Override
		public void onMediaPlayerStateChange(String state, String message) {
			
			Log.i(TAG, "Media Player State Change: " + state);
			
			if (state.equals(MyMediaPlayer.STATE_ERROR)) {

				if (Integer.parseInt(message) == 0) {
					
					myMediaPlayer.restartMediaPlayer();
					
				} else {
					
					exit(Integer.parseInt(message));
				}
				
			} else if (state.equals(MyMediaPlayer.STATE_DONE)) {

				// When the done button is clicked
				myMediaPlayer.releaseMediaPlayer();
				finish();
			
			} else if (state.equals(MyMediaPlayer.STATE_START)) {
				
				// the media player did start playing the video
				
			} else if (state.equals(MyMediaPlayer.STATE_END)) {

				// the media player did finish playing the video
			}
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_main);
        
        videoLayout = (RelativeLayout) findViewById(R.id.video);

        path = "http://videos.hd-trailers.net/man-of-steel-uk-trailer-480p.mp4";
        
		configureVideo();
    }
	
	@Override
	public void onPause() {
		super.onPause();
		
		Log.i(TAG, "onPause");
		
		if (myMediaPlayer != null) {
			myMediaPlayer.releaseMediaPlayer();
			myMediaPlayer = null;
		}
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		Log.i(TAG, "onResume");
		
		setRequestedOrientation(getResources().getConfiguration().orientation);
		
		configureVideo();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (myMediaPlayer != null) {
			myMediaPlayer.releaseMediaPlayer();
			myMediaPlayer = null;
		}
		
		cancelDownload();
	}

    private void cancelDownload() {

		if (downloading && downloader != null)
			downloader.cancel(true);
	}
	
	private void cacheVideo() {

		if (downloading)
			return;
		
		downloading = true;
		
		DialogUtils.showProgress(this, "Retrieving Cached Video...");
		
		downloader = new AsyncTask<Void, Void, Boolean>() {
    		
			@Override
			protected Boolean doInBackground(Void... params) {
				
				// set to true to re-download cached content
				boolean refresh = false;
				
				path = CacheUtils.cacheResource(path, refresh);
				
    			return true;
			}
    		
    		@Override
    		protected void onPostExecute(Boolean success) {
    			
    			downloading = false;
    			DialogUtils.hideProgress();
    			loadHomeVideo();
    		}
			
		};
		downloader.execute();
	}
	
	private void configureVideo() {
		
		// Set to always cache video, to just stream set to false
		if (true) {
			
			cacheVideo();
			
		} else {
		
			cancelDownload();
			
			loadHomeVideo();
		}
	}
	
	private void loadHomeVideo() {
		
    	Log.i(TAG, "loadHomeVideo " + (myMediaPlayer != null));

    	if (myMediaPlayer != null) {
        	
	 		myMediaPlayer.resetMediaPlayer();
	 		
        } else if (myMediaPlayer == null && path != null) {
    		
    		boolean loopVideo = false;
    		boolean hideControls = false;
    		boolean autoplay = true;
    		
    		myMediaPlayer = new MyMediaPlayer(this, videoLayout, path, mediaListener, hideControls, loopVideo, autoplay);
    	
    	} else {
    		
			exit(0);
    	}
    }
	
	private void exit(int code) {

		String msg = "The video could not be played";
		
		switch (code) {
		case 404:
			msg = "Video not found at path: " + path;
			break;
		case -2147483648:
			msg = "Unsupported format";
			break;
		}
		
		DialogUtils.alertDialog("Video Error", msg, this, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
		
		myMediaPlayer.releaseMediaPlayer();
		myMediaPlayer = null;
	}
    
}
