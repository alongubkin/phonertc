package com.dooble.phonertc;

import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;

public class VideoTrackRendererPair {
	private VideoTrack _videoTrack;
	private VideoRenderer _videoRenderer;
	
	public VideoTrackRendererPair(VideoTrack videoTrack, VideoRenderer videoRenderer) {
		_videoTrack = videoTrack;
		_videoRenderer = videoRenderer;
	}
	
	public VideoTrack getVideoTrack() {
		return _videoTrack;
	}
	
	public void setVideoTrack(VideoTrack _videoTrack) {
		this._videoTrack = _videoTrack;
	}

	public VideoRenderer getVideoRenderer() {
		return _videoRenderer;
	}

	public void setVideoRenderer(VideoRenderer _videoRenderer) {
		this._videoRenderer = _videoRenderer;
	}
}
