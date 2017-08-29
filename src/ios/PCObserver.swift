import Foundation

class PCObserver : NSObject, RTCPeerConnectionDelegate {
var session: Session

init(session: Session) {
    self.session = session
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    addedStream stream: RTCMediaStream!) {
    print("PCO onAddStream.")
        
    DispatchQueue.main.async {
        if stream.videoTracks.count > 0 {
            self.session.addVideoTrack(stream.videoTracks[0] as! RTCVideoTrack)
        }
    }
    
    self.session.sendMessage(
        "{\"type\": \"__answered\"}".data(using: String.Encoding.utf8)!)
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    removedStream stream: RTCMediaStream!) {
    print("PCO onRemoveStream.")
    
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    iceGatheringChanged newState: RTCICEGatheringState) {
    print("PCO onIceGatheringChange. \(newState)")
    
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    iceConnectionChanged newState: RTCICEConnectionState)
{
    print("PCO onIceConnectionChange. \(newState)")
    
}

func peerConnection(_ peerConnection: RTCPeerConnection,
    gotICECandidate candidate: RTCICECandidate ) {
    let getSdpMid : String = candidate.sdpMid as String;
    let getSdpLineIndex : Int = candidate.sdpMLineIndex as Int;
    let getSdp : String = candidate.sdp as String;
    
    print("PCO -- onICECandidate --Mid: \(getSdpMid) \n Index: \(getSdpLineIndex)   \n Sdp:\(getSdp) ")

    let json: [String: AnyObject ] = [
        "type": "candidate" as AnyObject,
        "label": candidate.sdpMLineIndex as AnyObject,
        "id": candidate.sdpMid as AnyObject,
        "candidate": candidate.sdp as AnyObject
    ]
        
    let data: Data?
    do {
        data = try JSONSerialization.data(withJSONObject: json,
                    options: JSONSerialization.WritingOptions())
    } catch let error as NSError {
        print( "error: \(error)" );
        data = nil
    }
        
    self.session.sendMessage(data!)
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    signalingStateChanged stateChanged: RTCSignalingState) {
    print("PCO onSignalingStateChange: \(stateChanged)")
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    didOpen dataChannel: RTCDataChannel!) {
    print("PCO didOpenDataChannel.")
}

func peerConnectionOnError(_ peerConnection: RTCPeerConnection!) {
    print("PCO onError.")
}

func peerConnection(onRenegotiationNeeded peerConnection: RTCPeerConnection!) {
    print("PCO onRenegotiationNeeded.")
    // TODO: Handle this
}
}
