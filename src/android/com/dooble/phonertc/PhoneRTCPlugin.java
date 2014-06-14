package com.dooble.phonertc;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

import android.media.AudioManager;
import android.util.Log;

public class PhoneRTCPlugin extends CordovaPlugin {
	public static final String ACTION_CALL = "call";
	public static final String ACTION_RECEIVE_MESSAGE = "receiveMessage";
	public static final String ACTION_DISCONNECT = "disconnect";
	
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
	
	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {
		
		if (action.equals(ACTION_CALL)) {
			isInitiator = args.getBoolean(0);
			
			final String turnServerHost = args.getString(1);
			final String turnUsername = args.getString(2);
			final String turnPassword = args.getString(3);
			
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
					audioManager.setMode(isWiredHeadsetOn ? AudioManager.MODE_IN_CALL
							: AudioManager.MODE_IN_COMMUNICATION);
					audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
					
					abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity()),
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
							"OfferToReceiveVideo", "false"));
		
					cordova.getActivity().runOnUiThread(new Runnable() {
						public void run() {
							factory = new PeerConnectionFactory();
							MediaConstraints pcMediaConstraints = new MediaConstraints();
							pcMediaConstraints.optional.add(new MediaConstraints.KeyValuePair(
								"DtlsSrtpKeyAgreement", "true"));
							pc = factory.createPeerConnection(iceServers, pcMediaConstraints,
									pcObserver);
							
							MediaStream lMS = factory.createLocalMediaStream("ARDAMS");
							lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));
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
		}

		callbackContext.error("Invalid action");
		return false;
	}

	void sendMessage(JSONObject data) {
		PluginResult result = new PluginResult(PluginResult.Status.OK, data);
		result.setKeepCallback(true);
		_callbackContext.sendPluginResult(result);
	}

	private String preferISAC(String sdpDescription) {
		String[] lines = sdpDescription.split("\n");
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
			newSdpDescription.append(line).append("\n");
		}
		return newSdpDescription.toString();
	}

	private static void abortUnless(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException(msg);
		}
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
		public void onAddStream(final MediaStream arg0) {
			// TODO Auto-generated method stub
			PhoneRTCPlugin.this.cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
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
		public void onDataChannel(DataChannel arg0) {
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
}
