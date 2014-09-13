package com.dooble.phonertc;

import org.webrtc.AudioTrack;
import org.webrtc.VideoTrack;

public class SessionConfig {
	private boolean _isInitiator;
	private String _turnServerHost;
	private String _turnServerUsername;
	private String _turnServerPassword;
	private VideoTrack _localVideoTrack;
	private AudioTrack _localAudioTrack;
	
	public String getTurnServerHost() {
		return _turnServerHost;
	}
	
	public void setTurnServerHost(String _turnServerHost) {
		this._turnServerHost = _turnServerHost;
	}

	public String getTurnServerUsername() {
		return _turnServerUsername;
	}

	public void setTurnServerUsername(String _turnServerUsername) {
		this._turnServerUsername = _turnServerUsername;
	}

	public String getTurnServerPassword() {
		return _turnServerPassword;
	}

	public void setTurnServerPassword(String _turnServerPassword) {
		this._turnServerPassword = _turnServerPassword;
	}

	public boolean isInitiator() {
		return _isInitiator;
	}

	public void setInitiator(boolean _isInitiator) {
		this._isInitiator = _isInitiator;
	}

	public VideoTrack getLocalVideoTrack() {
		return _localVideoTrack;
	}

	public void setLocalVideoTrack(VideoTrack _localVideoTrack) {
		this._localVideoTrack = _localVideoTrack;
	}

	public AudioTrack getLocalAudioTrack() {
		return _localAudioTrack;
	}

	public void setLocalAudioTrack(AudioTrack _localAudioTrack) {
		this._localAudioTrack = _localAudioTrack;
	}
}
