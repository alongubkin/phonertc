package com.dooble.phonertc;

import android.graphics.Point;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
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
		
	private VideoStreamsView _localVideoView;
	
	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		final CallbackContext _callbackContext = callbackContext;
		
		if (action.equals("createSessionObject")) {
			final SessionConfig config = new SessionConfig();
			config.setInitiator(args.getBoolean(0));
			config.setTurnServerHost(args.getString(1));
			config.setTurnServerUsername(args.getString(2));
			config.setTurnServerPassword(args.getString(3));
			
			final JSONObject video = args.isNull(4) ? null : args.getJSONObject(4);
			
			if (_peerConnectionFactory == null) {
				abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true),
						"Failed to initializeAndroidGlobals");
			}
			
			// TODO: Remove this and use AudioToggle instead
			AudioManager audioManager = ((AudioManager) cordova.getActivity().getSystemService(cordova.getActivity().AUDIO_SERVICE));
			@SuppressWarnings("deprecation")
			boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
			//audioManager.setMode(isWiredHeadsetOn ? AudioManager.MODE_IN_CALL
			//		: AudioManager.MODE_IN_COMMUNICATION);
			audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					_peerConnectionFactory = new PeerConnectionFactory();
					VideoStreamsView remoteVideoView = null;
					
					if (video != null) {
						if (_videoTrack == null) {
							initializeLocalVideoTrack();
						}
						
						try {
							remoteVideoView = createVideoView(video, false);
						} catch (JSONException e) {
							Log.e("com.dooble.phonertc", "A JSON exception has occured while trying to add video.", e);
						}
						
						config.setLocalVideoTrack(_videoTrack);
					}
					
					if (_audioTrack == null) {
						_audioSource = _peerConnectionFactory.createAudioSource(new MediaConstraints());
						_audioTrack = _peerConnectionFactory.createAudioTrack("ARDAMSa0", _audioSource);
					}
					
					config.setLocalAudioTrack(_audioTrack); 
					
					_session = new Session(cordova.getActivity(), 
										   webView,
										   remoteVideoView,
										   _peerConnectionFactory,
										   _callbackContext,
										   config);
					_session.initialize();
				}
			});
			
			return true;
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

		callbackContext.error("Invalid action");
		return false;
	}

	void initializeLocalVideoTrack() {
		_videoCapturer = getVideoCapturer();
		_videoSource = _peerConnectionFactory.createVideoSource(_videoCapturer, 
				new MediaConstraints());	
		
		_videoTrack = _peerConnectionFactory.createVideoTrack("ARDAMSv0", _videoSource);
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

	VideoStreamsView createVideoView(JSONObject config, boolean isMirrored) throws JSONException {
		WebView.LayoutParams params = getLayoutParams(config);

		Point displaySize = new Point(params.width, params.height);

		VideoStreamsView view = new VideoStreamsView(cordova.getActivity(), displaySize, isMirrored);
		webView.addView(view, params);

		return view;
	}
	
	private static void abortUnless(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException(msg);
		}
	}

	// Cycle through likely device names for the camera and return the first
	// capturer that works, or crash if none do.
	private VideoCapturer getVideoCapturer() {
		if (_videoCapturer != null) {
			return _videoCapturer;
		}
		
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
