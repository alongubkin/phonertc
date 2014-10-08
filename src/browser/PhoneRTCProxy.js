var PeerConnection = window.mozRTCPeerConnection || window.webkitRTCPeerConnection;
var IceCandidate = window.mozRTCIceCandidate || window.RTCIceCandidate;
var SessionDescription = window.mozRTCSessionDescription || window.RTCSessionDescription;
navigator.getUserMedia = navigator.getUserMedia || navigator.mozGetUserMedia || navigator.webkitGetUserMedia;

var localVideo = document.createElement('video');
var remoteVideo = document.createElement('video');
localVideo.autoplay = true;
localVideo.muted = true;
remoteVideo.autoplay = true;

document.body.appendChild(localVideo);
document.body.appendChild(remoteVideo);

var config;

var peerConnection;
var sendMessageCallback;

function createSessionObject(success, error, opts) {
	config = opts[0];
	sendMessageCallback = success;
}

function call(success, error, opts) {
	navigator.getUserMedia(config.streams, function (stream) {
		attachMediaStream(localVideo, stream);
		
		peerConnection = new PeerConnection({
			iceServers: [
				{ url: 'stun:stun.l.google.com:19302' },
				{ url: config.turn.host, username: config.turn.username, password: config.turn.password }
			]
		}, { optional: [ { DtlsSrtpKeyAgreement: true } ]});

		peerConnection.onicecandidate = onIceCandidate;
		peerConnection.onaddstream = onRemoteStreamAdded;

		peerConnection.addStream(stream);

		if (config.isInitiator) {
	    peerConnection.createOffer(function (sessionDescription) {

				peerConnection.setLocalDescription(sessionDescription, function () {
					console.log('Set session description success.');
				}, function (error) {
					console.log(error);
				});

				sendMessage(sessionDescription);
	    }, function (error) {
	    	console.log(error);
	    }, { mandatory: { OfferToReceiveAudio: true, OfferToReceiveVideo: false }});
		}
	}, function (error) {
		console.log(error);
	});
}

function receiveMessage(success, error, opts) {
	var message = JSON.parse(opts[0]);
  if (message.type === 'offer') {
    setRemote(message);
    peerConnection.createAnswer(function (sessionDescription) {

			peerConnection.setLocalDescription(sessionDescription, function () {
				console.log('Set session description success.');
			}, function (error) {
				console.log(error);
			});

			sendMessage(sessionDescription);
    }, function (error) {
    	console.log(error);
    }, { mandatory: { OfferToReceiveAudio: true, OfferToReceiveVideo: false }});
  } else if (message.type === 'answer') {
    setRemote(message);
  } else if (message.type === 'candidate') {
    var candidate = new RTCIceCandidate({
      sdpMLineIndex: message.label,
      candidate: message.candidate
    });
    
    peerConnection.addIceCandidate(candidate, function () {
    	console.log('Remote candidate added successfully.');
    }, function (error) {
    	console.log(error);
    });
     
  } else if (message.type === 'bye') {
    console.log('disconnect');
    //onRemoteHangup();
  }
}

function disconnect() {
	console.log('disconnect');
}

function onIceCandidate(event) {
  if (event.candidate) {
    sendMessage({
      type: 'candidate',
      label: event.candidate.sdpMLineIndex,
      id: event.candidate.sdpMid,
      candidate: event.candidate.candidate
    });
  }
}

function onRemoteStreamAdded(event) {
	console.log('attaching remote video');
  attachMediaStream(remoteVideo, event.stream);
}

function sendMessage(data) {
	sendMessageCallback(data);
}

function setRemote(message) {
  message.sdp = addCodecParam(message.sdp, 'opus/48000', 'stereo=1');

  peerConnection.setRemoteDescription(new SessionDescription(message), function () {
  	console.log('setRemote success');
  }, function (error) { 
  	console.log(error); 
  });
}

// Adds fmtp param to specified codec in SDP.
function addCodecParam(sdp, codec, param) {
  var sdpLines = sdp.split('\r\n');

  // Find opus payload.
  var index = findLine(sdpLines, 'a=rtpmap', codec);
  var payload;
  if (index) {
    payload = getCodecPayloadType(sdpLines[index]);
  }

  // Find the payload in fmtp line.
  var fmtpLineIndex = findLine(sdpLines, 'a=fmtp:' + payload.toString());
  if (fmtpLineIndex === null) {
    return sdp;
  }

  sdpLines[fmtpLineIndex] = sdpLines[fmtpLineIndex].concat('; ', param);

  sdp = sdpLines.join('\r\n');
  return sdp;
}

// Find the line in sdpLines that starts with |prefix|, and, if specified,
// contains |substr| (case-insensitive search).
function findLine(sdpLines, prefix, substr) {
  return findLineInRange(sdpLines, 0, -1, prefix, substr);
}

// Find the line in sdpLines[startLine...endLine - 1] that starts with |prefix|
// and, if specified, contains |substr| (case-insensitive search).
function findLineInRange(sdpLines, startLine, endLine, prefix, substr) {
  var realEndLine = endLine !== -1 ? endLine : sdpLines.length;
  for (var i = startLine; i < realEndLine; ++i) {
    if (sdpLines[i].indexOf(prefix) === 0) {
      if (!substr ||
          sdpLines[i].toLowerCase().indexOf(substr.toLowerCase()) !== -1) {
        return i;
      }
    }
  }
  return null;
}

// Gets the codec payload type from an a=rtpmap:X line.
function getCodecPayloadType(sdpLine) {
  var pattern = new RegExp('a=rtpmap:(\\d+) \\w+\\/\\d+');
  var result = sdpLine.match(pattern);
  return (result && result.length === 2) ? result[1] : null;
}

// Returns a new m= line with the specified codec as the first one.
function setDefaultCodec(mLine, payload) {
  var elements = mLine.split(' ');
  var newLine = [];
  var index = 0;
  for (var i = 0; i < elements.length; i++) {
    if (index === 3) { // Format of media starts from the fourth.
      newLine[index++] = payload; // Put target payload to the first.
    }
    if (elements[i] !== payload) {
      newLine[index++] = elements[i];
    }
  }
  return newLine.join(' ');
}

function attachMediaStream(element, stream) {
  element.src = URL.createObjectURL(stream);
  element.load()
}

module.exports = {
	createSessionObject: createSessionObject,
	call: call,
	receiveMessage: receiveMessage,
	disconnect: disconnect
};

require("cordova/exec/proxy").add("PhoneRTCPlugin", module.exports);