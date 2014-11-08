package com.dooble.phonertc;

import org.json.JSONException;
import org.json.JSONObject;

public class SessionConfig {
	private boolean _isInitiator;
	private String _turnServerHost;
	private String _turnServerUsername;
	private String _turnServerPassword;
	private boolean _audioStreamEnabled;
	private boolean _videoStreamEnabled;
	
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
	
	public boolean isAudioStreamEnabled() {
		return _audioStreamEnabled;
	}

	public void setAudioStreamEnabled(boolean _audioStreamEnabled) {
		this._audioStreamEnabled = _audioStreamEnabled;
	}

	public boolean isVideoStreamEnabled() {
		return _videoStreamEnabled;
	}

	public void setVideoStreamEnabled(boolean _videoStreamEnabled) {
		this._videoStreamEnabled = _videoStreamEnabled;
	}

	public static SessionConfig fromJSON(JSONObject json) throws JSONException {
		SessionConfig config = new SessionConfig();
		config.setInitiator(json.getBoolean("isInitiator"));
		
		JSONObject turn = json.getJSONObject("turn");
		config.setTurnServerHost(turn.getString("host"));
		config.setTurnServerUsername(turn.getString("username"));
		config.setTurnServerPassword(turn.getString("password"));

		JSONObject streams = json.getJSONObject("streams");
		config.setAudioStreamEnabled(streams.getBoolean("audio"));
		config.setVideoStreamEnabled(streams.getBoolean("video"));
		
		return config;
	}
}
