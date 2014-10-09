package com.dooble.phonertc;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.graphics.Point;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

public class PhoneRTCPlugin extends CordovaPlugin {
	private AudioSource _audioSource;
	private AudioTrack _audioTrack;

	private VideoCapturer _videoCapturer;
	private VideoSource _videoSource;
	
	private PeerConnectionFactory _peerConnectionFactory;
	private Session _session; // TODO: Map<String, Session>
	
	private VideoConfig _videoConfig;
	private VideoGLView _videoView;
	private List<VideoTrackRendererPair> _remoteVideos;
	private VideoTrackRendererPair _localVideo;
	
	public PhoneRTCPlugin() {
		_remoteVideos = new ArrayList<VideoTrackRendererPair>();
	}
	
	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		final CallbackContext _callbackContext = callbackContext;
		
		if (action.equals("createSessionObject")) {		
			final SessionConfig config = SessionConfig.fromJSON(args.getJSONObject(0));
			
			PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
			result.setKeepCallback(true);
			_callbackContext.sendPluginResult(result);
			
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					if (_peerConnectionFactory == null) {
						abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true, 
								VideoRendererGui.getEGLContext()),
								"Failed to initializeAndroidGlobals");
						_peerConnectionFactory = new PeerConnectionFactory();
					}
			
					if (config.isAudioStreamEnabled() && _audioTrack == null) {
						initializeLocalAudioTrack();
					}
					
					if (config.isVideoStreamEnabled() && _localVideo == null) {		
						initializeLocalVideoTrack();
					}
					
					_session = new Session(PhoneRTCPlugin.this, _callbackContext, config);
				}
			});
			
			return true;
		} else if (action.equals("call")) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					_session.call();
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
			cordova.getThreadPool().execute(new Runnable() {
				@Override
				public void run() {
					_session.disconnect();
				}
			});
			
			return true;
		} else if (action.equals("setVideoView")) {
			_videoConfig = VideoConfig.fromJSON(args.getJSONObject(0));
			
			// make sure it's not junk
			if (_videoConfig.getContainer().getWidth() == 0 || _videoConfig.getContainer().getHeight() == 0) {
				return false;
			}
					
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					if (_peerConnectionFactory == null) {
						abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true, 
								VideoRendererGui.getEGLContext()),
								"Failed to initializeAndroidGlobals");
						_peerConnectionFactory = new PeerConnectionFactory();
					}
					
					@SuppressWarnings("deprecation")
					WebView.LayoutParams params = new WebView.LayoutParams(
							_videoConfig.getContainer().getWidth() * _videoConfig.getDevicePixelRatio(), 
							_videoConfig.getContainer().getHeight() * _videoConfig.getDevicePixelRatio(), 
							_videoConfig.getContainer().getX() * _videoConfig.getDevicePixelRatio(), 
							_videoConfig.getContainer().getY() * _videoConfig.getDevicePixelRatio());
				
					if (_videoView == null) {
						Point size = new Point();
						size.set(_videoConfig.getContainer().getWidth() * _videoConfig.getDevicePixelRatio(), 
								_videoConfig.getContainer().getHeight() * _videoConfig.getDevicePixelRatio());
				
						_videoView = new VideoGLView(cordova.getActivity(), size);
						VideoRendererGui.setView(_videoView);
					
						webView.addView(_videoView, params);
					
						if (_videoConfig.getLocal() != null && _localVideo == null) {
							initializeLocalVideoTrack();
						}
					} else {
						_videoView.setLayoutParams(params);
					}
				}
			});
			
			return true;
		}

		callbackContext.error("Invalid action: " + action);
		return false;
	}

	void initializeLocalVideoTrack() {
		_videoCapturer = getVideoCapturer();
		_videoSource = _peerConnectionFactory.createVideoSource(_videoCapturer, 
				new MediaConstraints());
		
		_localVideo = new VideoTrackRendererPair(_peerConnectionFactory.createVideoTrack("ARDAMSv0", _videoSource), null);
		refreshVideoView();
	}
	
	int getPercentage(int localValue, int containerValue) {
		return (int)(localValue * 100.0 / containerValue);
	}
	
	void initializeLocalAudioTrack() {
		_audioSource = _peerConnectionFactory.createAudioSource(new MediaConstraints());
		_audioTrack = _peerConnectionFactory.createAudioTrack("ARDAMSa0", _audioSource);
	}
	
	public VideoTrack getLocalVideoTrack() {
		if (_localVideo == null) {
			return null;
		}
		
		return _localVideo.getVideoTrack();
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
	
	public VideoConfig getVideoConfig() {
		return this._videoConfig;
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
	
	public void addRemoteVideoTrack(VideoTrack videoTrack) {
		_remoteVideos.add(new VideoTrackRendererPair(videoTrack, null));
		refreshVideoView();
	}

	private void refreshVideoView() {
		int n = _remoteVideos.size();
		
		if (n > 0) {			
			int totalArea = _videoConfig.getContainer().getWidth() * _videoConfig.getContainer().getHeight();
			int videoSize = (int)Math.sqrt((float)totalArea / n);
			
			int videosInRow = (int)((float)_videoConfig.getContainer().getWidth() / videoSize);
			int rows = (int)Math.ceil((float)n / videosInRow);
			
			int videoIndex = 0;
			
			int videoSizeAsPercentage = getPercentage(videoSize, _videoConfig.getContainer().getWidth());
			
			for (int row = 0; row < rows; row++) {
				int y = getPercentage(row, rows);
				
				for (int video = 0; video < videosInRow; video++) {
					VideoTrackRendererPair pair = _remoteVideos.get(videoIndex++);
					
					if (pair.getVideoRenderer() != null) {
						pair.getVideoTrack().removeRenderer(pair.getVideoRenderer());
					}
					
					int x = getPercentage(video, videosInRow);
					
					pair.setVideoRenderer(new VideoRenderer(
							VideoRendererGui.create(x, y, videoSizeAsPercentage, videoSizeAsPercentage, 
									VideoRendererGui.ScalingType.SCALE_FILL)));
				
					pair.getVideoTrack().addRenderer(pair.getVideoRenderer());
				}
			}
		}
			
		if (_videoConfig.getLocal() != null && _localVideo != null) {
			if (_localVideo.getVideoRenderer() != null) {
				_localVideo.getVideoTrack().removeRenderer(_localVideo.getVideoRenderer());
			}
			
			_localVideo.getVideoTrack().addRenderer(new VideoRenderer(
					VideoRendererGui.create(getPercentage(_videoConfig.getLocal().getX(), _videoConfig.getContainer().getX()), 
											getPercentage(_videoConfig.getLocal().getY(), _videoConfig.getContainer().getY()), 
											getPercentage(_videoConfig.getLocal().getWidth(), _videoConfig.getContainer().getWidth()), 
											getPercentage(_videoConfig.getLocal().getHeight(), _videoConfig.getContainer().getHeight()), 
											VideoRendererGui.ScalingType.SCALE_FILL)));
			
		}
	}
}
