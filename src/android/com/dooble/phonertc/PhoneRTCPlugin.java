package com.dooble.phonertc;

import android.app.Activity;
import android.graphics.Point;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.media.AudioManager;
import android.util.Log;

public class PhoneRTCPlugin extends CordovaPlugin {
	private AudioSource _audioSource;
	private AudioTrack _audioTrack;

	private VideoCapturer _videoCapturer;
	private VideoSource _videoSource;

	private PeerConnectionFactory _peerConnectionFactory;
	private Session _session; // TODO: Map<String, Session>
	private VideoTrack _videoTrack;
	
	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		final CallbackContext _callbackContext = callbackContext;
		
		if (action.equals("createSessionObject")) {		
			final SessionConfig config = SessionConfig.fromJSON(args.getJSONObject(0));
			
			PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
			result.setKeepCallback(true);
			_callbackContext.sendPluginResult(result);
			
			if (_peerConnectionFactory == null) {
				abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true, 
						VideoRendererGui.getEGLContext()),
						"Failed to initializeAndroidGlobals");
			}
			
			_peerConnectionFactory = new PeerConnectionFactory();
			
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					/* Point displaySize = new Point();
					    cordova.getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);

						VideoGLView videoView = new VideoGLView(cordova.getActivity(), displaySize);
						VideoRendererGui.setView(videoView);
						
						webView.addView(videoView);
					*/
					
					if (config.isAudioStreamEnabled() && _audioTrack == null) {
						initializeLocalAudioTrack();
					}
					
					if (config.isVideoStreamEnabled() && _videoTrack == null) {		
						initializeLocalVideoTrack();
					}
					
					_session = new Session(PhoneRTCPlugin.this, _callbackContext, config);	
				}
			});
			
			return true;
		} else if (action.equals("call")) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					_session.initialize();
				}
			});
		} else if (action.equals("receiveMessage")) {
			final String message = args.getString(0);

			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					_session.receiveMessage(message);
				}
			});

			return true;
		} else if (action.equals("disconnect")) {
			Log.e("com.dooble.phonertc", "DISCONNECT");
			_session.disconnect();
			return true;
		}

		callbackContext.error("Invalid action: " + action);
		return false;
	}

	void initializeLocalVideoTrack() {
		_videoCapturer = getVideoCapturer();
		_videoSource = _peerConnectionFactory.createVideoSource(_videoCapturer, 
				new MediaConstraints());	
		
		_videoTrack = _peerConnectionFactory.createVideoTrack("ARDAMSv0", _videoSource);
		_videoTrack.addRenderer(new VideoRenderer(VideoRendererGui.create(100, 100, 100, 100)));
	}
	
	void initializeLocalAudioTrack() {
		_audioSource = _peerConnectionFactory.createAudioSource(new MediaConstraints());
		_audioTrack = _peerConnectionFactory.createAudioTrack("ARDAMSa0", _audioSource);
	}
	
	WebView.LayoutParams getLayoutParams (JSONObject config) throws JSONException {
		int devicePixelRatio = config.getInt("devicePixelRatio");

		int width = config.getInt("width") * devicePixelRatio;
		int height = config.getInt("height") * devicePixelRatio;
		int x = config.getInt("x") * devicePixelRatio;
		int y = config.getInt("y") * devicePixelRatio;

		WebView.LayoutParams params = new WebView.LayoutParams(width, height, x, y);

		return params;
	}
	
	public VideoTrack getLocalVideoTrack() {
		return _videoTrack;
	}
	
	public AudioTrack getLocalAudioTrack() {
		return _audioTrack;
	}
	
	public PeerConnectionFactory getPeerConnectionFactory() {
		return _peerConnectionFactory;
	}
	
	public Activity getActivity() {
		return cordova.getActivity();
	}
	
	public WebView getWebView() {
		return this.getWebView();
	}
	
	private static void abortUnless(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException(msg);
		}
	}

	// Cycle through likely device names for the camera and return the first
	// capturer that works, or crash if none do.
	private VideoCapturer getVideoCapturer() {
		String[] cameraFacing = { "front", "back" };
		int[] cameraIndex = { 0, 1 };
		int[] cameraOrientation = { 0, 90, 180, 270 };
		for (String facing : cameraFacing) {
			for (int index : cameraIndex) {
				for (int orientation : cameraOrientation) {
					String name = "Camera " + index + ", Facing " + facing +
						", Orientation " + orientation;
					VideoCapturer capturer = VideoCapturer.create(name);
					if (capturer != null) {
						// logAndToast("Using camera: " + name);
						return capturer;
					}
				}
			}
		}
		throw new RuntimeException("Failed to open capturer");
	}
}
