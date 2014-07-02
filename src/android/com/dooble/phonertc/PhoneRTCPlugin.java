package com.dooble.phonertc;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Point;
import android.webkit.WebView;


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRenderer.I420Frame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.media.AudioManager;
import android.util.Log;

public class PhoneRTCPlugin extends CordovaPlugin {
	public static final String ACTION_CALL = "call";
	public static final String ACTION_RECEIVE_MESSAGE = "receiveMessage";
	public static final String ACTION_DISCONNECT = "disconnect";
	public static final String ACTION_UPDATE_VIDEO_POSITION = "updateVideoPosition";

	CallbackContext _callbackContext;

	private final SDPObserver sdpObserver = new SDPObserver();
	private final PCObserver pcObserver = new PCObserver();

	private boolean isInitiator;

	private PeerConnectionFactory factory;
	private PeerConnection pc;
	private MediaConstraints sdpMediaConstraints;

	private LinkedList<IceCandidate> queuedRemoteCandidates ;

	// Synchronize on quit[0] to avoid teardown-related crashes.
	private final Boolean[] quit = new Boolean[] { false };

	private AudioSource audioSource;

	private VideoCapturer videoCapturer;
	private VideoSource videoSource;
	private VideoStreamsView localVideoView;
	private VideoStreamsView remoteVideoView;

	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		if (action.equals(ACTION_CALL)) {
			isInitiator = args.getBoolean(0);

			final String turnServerHost = args.getString(1);
			final String turnUsername = args.getString(2);
			final String turnPassword = args.getString(3);
			final JSONObject video = (!args.isNull(4)) ? args.getJSONObject(4) : null;

			_callbackContext = callbackContext;
			queuedRemoteCandidates = new LinkedList<IceCandidate>();
			quit[0] = false;

			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
					result.setKeepCallback(true);
					_callbackContext.sendPluginResult(result);

					AudioManager audioManager = ((AudioManager) cordova.getActivity().getSystemService(cordova.getActivity().AUDIO_SERVICE));
					@SuppressWarnings("deprecation")
					boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
					//audioManager.setMode(isWiredHeadsetOn ? AudioManager.MODE_IN_CALL
					//		: AudioManager.MODE_IN_COMMUNICATION);
					audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

					abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true),
							"Failed to initializeAndroidGlobals");

					final LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
					iceServers.add(new PeerConnection.IceServer(
							"stun:stun.l.google.com:19302"));
					iceServers.add(new PeerConnection.IceServer(
							turnServerHost, turnUsername, turnPassword));

					sdpMediaConstraints = new MediaConstraints();
					sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
							"OfferToReceiveAudio", "true"));
					sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
							"OfferToReceiveVideo", (video != null) ? "true" : "false"));

					cordova.getActivity().runOnUiThread(new Runnable() {
						public void run() {
							factory = new PeerConnectionFactory();
							MediaConstraints pcMediaConstraints = new MediaConstraints();
							pcMediaConstraints.optional.add(new MediaConstraints.KeyValuePair(
								"DtlsSrtpKeyAgreement", "true"));
							pc = factory.createPeerConnection(iceServers, pcMediaConstraints,
									pcObserver);

							MediaStream lMS = factory.createLocalMediaStream("ARDAMS");

							if (video != null) {
								try {
									localVideoView = createVideoView(video.getJSONObject("localVideo"));
									remoteVideoView = createVideoView(video.getJSONObject("remoteVideo"));

									VideoCapturer capturer = getVideoCapturer();
									videoSource = factory.createVideoSource(capturer, new MediaConstraints());
									VideoTrack videoTrack =
										factory.createVideoTrack("ARDAMSv0", videoSource);
									videoTrack.addRenderer(new VideoRenderer(new VideoCallbacks(
										localVideoView, VideoStreamsView.Endpoint.REMOTE)));
									lMS.addTrack(videoTrack);
								} catch (JSONException e) {
									Log.e("com.dooble.phonertc", "A JSON exception has occured while trying to add video.", e);
								}
							}

							audioSource = factory.createAudioSource(new MediaConstraints());
							lMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));
							pc.addStream(lMS, new MediaConstraints());

							if (isInitiator) {
								pc.createOffer(sdpObserver, sdpMediaConstraints);
							}

						}
					});
					}
			});

			return true;
		} else if (action.equals(ACTION_RECEIVE_MESSAGE)) {
			final String message = args.getString(0);

			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					try {
						JSONObject json = new JSONObject(message);
						String type = (String) json.get("type");
						if (type.equals("candidate")) {
							final IceCandidate candidate = new IceCandidate(
									(String) json.get("id"), json.getInt("label"),
									(String) json.get("candidate"));
							if (queuedRemoteCandidates != null) {
								queuedRemoteCandidates.add(candidate);
							} else {
								cordova.getActivity().runOnUiThread(new Runnable() {
									public void run() {
										pc.addIceCandidate(candidate);
									}
								});
							}
						} else if (type.equals("answer") || type.equals("offer")) {
							final SessionDescription sdp = new SessionDescription(
									SessionDescription.Type.fromCanonicalForm(type),
									preferISAC((String) json.get("sdp")));
							cordova.getActivity().runOnUiThread(new Runnable() {
								public void run() {
									pc.setRemoteDescription(sdpObserver, sdp);
								}
							});
						} else if (type.equals("bye")) {
							Log.d("com.dooble.phonertc", "Remote end hung up; dropping PeerConnection");

							cordova.getActivity().runOnUiThread(new Runnable() {
								public void run() {
									disconnect();
								}
							});
						} else {
							//throw new RuntimeException("Unexpected message: " + message);
						}
					} catch (JSONException e) {
						throw new RuntimeException(e);
					}
				}
			});

			return true;
		} else if (action.equals(ACTION_DISCONNECT)) {
			Log.e("com.dooble.phonertc", "DISCONNECT");
			disconnect();
		} else if (action.equals(ACTION_UPDATE_VIDEO_POSITION)) {
			final JSONObject videoElements = args.getJSONObject(0);
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run () {
					try {
						if (localVideoView != null && videoElements.has("localVideo")) {
							localVideoView.setLayoutParams(getLayoutParams(videoElements.getJSONObject("localVideo")));
						}
						if (remoteVideoView != null && videoElements.has("remoteVideo")) {
							remoteVideoView.setLayoutParams(getLayoutParams(videoElements.getJSONObject("remoteVideo")));
						}
					} catch (JSONException e) {
						throw new RuntimeException(e);
					}
				}
			});
		}

		callbackContext.error("Invalid action");
		return false;
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

	VideoStreamsView createVideoView(JSONObject config) throws JSONException {
		WebView.LayoutParams params = getLayoutParams(config);

		Point displaySize = new Point(params.width, params.height);

		VideoStreamsView view = new VideoStreamsView(cordova.getActivity(), displaySize);
		webView.addView(view, params);

		return view;
	}

	void sendMessage(JSONObject data) {
		PluginResult result = new PluginResult(PluginResult.Status.OK, data);
		result.setKeepCallback(true);
		_callbackContext.sendPluginResult(result);
	}

	private String preferISAC(String sdpDescription) {
		String[] lines = sdpDescription.split("\r?\n");
		int mLineIndex = -1;
		String isac16kRtpMap = null;
		Pattern isac16kPattern = Pattern
				.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
		for (int i = 0; (i < lines.length)
				&& (mLineIndex == -1 || isac16kRtpMap == null); ++i) {
			if (lines[i].startsWith("m=audio ")) {
				mLineIndex = i;
				continue;
			}
			Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
			if (isac16kMatcher.matches()) {
				isac16kRtpMap = isac16kMatcher.group(1);
				continue;
			}
		}
		if (mLineIndex == -1) {
			Log.d("com.dooble.phonertc",
					"No m=audio line, so can't prefer iSAC");
			return sdpDescription;
		}
		if (isac16kRtpMap == null) {
			Log.d("com.dooble.phonertc",
					"No ISAC/16000 line, so can't prefer iSAC");
			return sdpDescription;
		}
		String[] origMLineParts = lines[mLineIndex].split(" ");
		StringBuilder newMLine = new StringBuilder();
		int origPartIndex = 0;
		// Format is: m=<media> <port> <proto> <fmt> ...
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(origMLineParts[origPartIndex++]).append(" ");
		newMLine.append(isac16kRtpMap).append(" ");
		for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
			if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
				newMLine.append(origMLineParts[origPartIndex]).append(" ");
			}
		}
		lines[mLineIndex] = newMLine.toString();
		StringBuilder newSdpDescription = new StringBuilder();
		for (String line : lines) {
			newSdpDescription.append(line).append("\r\n");
		}
		return newSdpDescription.toString();
	}

	private static void abortUnless(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException(msg);
		}
	}

	// Cycle through likely device names for the camera and return the first
	// capturer that works, or crash if none do.
	private VideoCapturer getVideoCapturer() {
		if (videoCapturer != null) {
			return videoCapturer;
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

	private class PCObserver implements PeerConnection.Observer {

		@Override
		public void onIceCandidate(final IceCandidate iceCandidate) {
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					try {
						JSONObject json = new JSONObject();
						json.put("type", "candidate");
						json.put("label", iceCandidate.sdpMLineIndex);
						json.put("id", iceCandidate.sdpMid);
						json.put("candidate", iceCandidate.sdp);
						sendMessage(json);
					} catch (JSONException e) {
						// TODO Auto-generated catch bloc
						e.printStackTrace();
					}
				}
			});
		}

		@Override
		public void onAddStream(final MediaStream stream) {
			// TODO Auto-generated method stub
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					if (remoteVideoView != null) {
						stream.videoTracks.get(0).addRenderer(new VideoRenderer(
							new VideoCallbacks(remoteVideoView, VideoStreamsView.Endpoint.REMOTE)));
					}

					try {
						JSONObject data = new JSONObject();
						data.put("type", "__answered");
						sendMessage(data);
					} catch (JSONException e) {

					}
				}
			});
		}

		@Override
		public void onDataChannel(DataChannel stream) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onError() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onIceConnectionChange(IceConnectionState arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onIceGatheringChange(IceGatheringState arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onRemoveStream(MediaStream arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onRenegotiationNeeded() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onSignalingChange(
				PeerConnection.SignalingState signalingState) {

		}

	}

	private class SDPObserver implements SdpObserver {
		@Override
		public void onCreateSuccess(final SessionDescription origSdp) {
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					SessionDescription sdp = new SessionDescription(
							origSdp.type, preferISAC(origSdp.description));
					try {
						JSONObject json = new JSONObject();
						json.put("type", sdp.type.canonicalForm());
						json.put("sdp", sdp.description);
						sendMessage(json);
						pc.setLocalDescription(sdpObserver, sdp);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		}

		@Override
		public void onSetSuccess() {
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					if (isInitiator) {
						if (pc.getRemoteDescription() != null) {
							// We've set our local offer and received & set the
							// remote
							// answer, so drain candidates.
							drainRemoteCandidates();
						}
					} else {
						if (pc.getLocalDescription() == null) {
							// We just set the remote offer, time to create our
							// answer.
							pc.createAnswer(SDPObserver.this,
									sdpMediaConstraints);
						} else {
							// Sent our answer and set it as local description;
							// drain
							// candidates.
							drainRemoteCandidates();
						}
					}
				}
			});
		}

		@Override
		public void onCreateFailure(final String error) {
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					throw new RuntimeException("createSDP error: " + error);
				}
			});
		}

		@Override
		public void onSetFailure(final String error) {
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					//throw new RuntimeException("setSDP error: " + error);
				}
			});
		}

		private void drainRemoteCandidates() {
			if (queuedRemoteCandidates == null)
				return;

			for (IceCandidate candidate : queuedRemoteCandidates) {
				pc.addIceCandidate(candidate);
			}
			queuedRemoteCandidates = null;
		}
	}

	private void disconnect() {
		synchronized (quit[0]) {
			if (quit[0]) {
				return;
			}
			quit[0] = true;
			if (pc != null) {
				pc.dispose();
				pc = null;
			}

			try {
				JSONObject json = new JSONObject();
				json.put("type", "bye");
				sendMessage(json);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					if (localVideoView != null) {
						webView.removeView(localVideoView);
						localVideoView = null;
					}

					if (remoteVideoView != null) {
						webView.removeView(remoteVideoView);
						remoteVideoView = null;
					}
				}
			});

			if (videoSource != null) {
				videoSource.dispose();
				videoSource = null;
			}

			if (factory != null) {
				factory.dispose();
				factory = null;
			}
		}

		try {
			JSONObject data = new JSONObject();
			data.put("type", "__disconnected");
			sendMessage(data);
		} catch (JSONException e) {

		}
	}

	// Implementation detail: bridge the VideoRenderer.Callbacks interface to the
	// VideoStreamsView implementation.
	private class VideoCallbacks implements VideoRenderer.Callbacks {
		private final VideoStreamsView view;
		private final VideoStreamsView.Endpoint stream;

		public VideoCallbacks(
			VideoStreamsView view, VideoStreamsView.Endpoint stream) {
			this.view = view;
			this.stream = stream;
			Log.d("CordovaLog", "VideoCallbacks");
		}

		@Override
		public void setSize(final int width, final int height) {
			Log.d("setSize", width + " " + height);
			view.queueEvent(new Runnable() {
				public void run() {
					view.setSize(stream, width, height);
				}
			});
		}

		@Override
		public void renderFrame(I420Frame frame) {
			view.queueFrame(stream, frame);
		}
	}
}
