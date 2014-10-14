package com.dooble.phonertc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class VideoConfig {
	
	private VideoLayoutParams _container;
	private VideoLayoutParams _local;
	private int _devicePixelRatio;
	
	public VideoLayoutParams getContainer() {
		return _container;
	}

	public void setContainer(VideoLayoutParams _container) {
		this._container = _container;
	}
	
	public VideoLayoutParams getLocal() {
		return _local;
	}

	public void setLocal(VideoLayoutParams _local) {
		this._local = _local;
	}
	
	public static VideoConfig fromJSON(JSONObject json) throws JSONException {
		VideoConfig config = new VideoConfig();
		config.setContainer(VideoLayoutParams.fromJSON(json.getJSONObject("containerParams")));
		config.setDevicePixelRatio(json.getInt("devicePixelRatio"));
		
		if (json.has("local")) {
			config.setLocal(VideoLayoutParams.fromJSON(json.getJSONObject("local")));
		}
		
		return config;
	}

	public int getDevicePixelRatio() {
		return _devicePixelRatio;
	}

	public void setDevicePixelRatio(int _devicePixelRatio) {
		this._devicePixelRatio = _devicePixelRatio;
	}

	public static class VideoLayoutParams {
		private int _x;
		private int _y;
		private int _width;
		private int _height;
		
		public int getX() {
			return _x;
		}
		
		public void setX(int _x) {
			this._x = _x;
		}

		public int getY() {
			return _y;
		}

		public void setY(int _y) {
			this._y = _y;
		}

		public int getWidth() {
			return _width;
		}

		public void setWidth(int _width) {
			this._width = _width;
		}

		public int getHeight() {
			return _height;
		}

		public void setHeight(int _height) {
			this._height = _height;
		}
		
		public static VideoLayoutParams fromJSON(JSONObject json) throws JSONException {
			VideoLayoutParams params = new VideoLayoutParams();
			
			JSONArray position = json.getJSONArray("position");
			params.setX(position.getInt(0));
			params.setY(position.getInt(1));
			
			JSONArray size = json.getJSONArray("size");
			params.setWidth(size.getInt(0));
			params.setHeight(size.getInt(1));
			
			return params;
		}
	}
}
