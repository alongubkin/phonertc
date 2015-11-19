import Foundation

class PCObserver : NSObject, RTCPeerConnectionDelegate {
    var session: Session
    
    init(session: Session) {
        self.session = session
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        addedStream stream: RTCMediaStream!) {
        print("PCO onAddStream.")
            
        dispatch_async(dispatch_get_main_queue()) {
            if stream.videoTracks.count > 0 {
                self.session.addVideoTrack(stream.videoTracks[0] as! RTCVideoTrack)
            }
        }
        
        self.session.sendMessage(
            "{\"type\": \"__answered\"}".dataUsingEncoding(NSUTF8StringEncoding)!)
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        removedStream stream: RTCMediaStream!) {
        print("PCO onRemoveStream.")
        /*
        dispatch_async(dispatch_get_main_queue()) {
            if stream.videoTracks.count > 0 {
                self.session.removeVideoTrack(stream.videoTracks[0] as RTCVideoTrack)
            }
        }*/
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        iceGatheringChanged newState: RTCICEGatheringState) {
        print("PCO onIceGatheringChange. \(newState)")
        
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        iceConnectionChanged newState: RTCICEConnectionState) {
        print("PCO onIceConnectionChange. \(newState)")
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        gotICECandidate candidate: RTCICECandidate!) {
        print("PCO onICECandidate.\n  Mid[\(candidate.sdpMid)] Index[\(candidate.sdpMLineIndex)] Sdp[\(candidate.sdp)]")
            
        var jsonError: NSError?

        let json: AnyObject = [
            "type": "candidate",
            "label": candidate.sdpMLineIndex,
            "id": candidate.sdpMid,
            "candidate": candidate.sdp
        ]
            
        let data: NSData?
        do {
            data = try NSJSONSerialization.dataWithJSONObject(json,
                        options: NSJSONWritingOptions())
        } catch let error as NSError {
            jsonError = error
            data = nil
        }
            
        self.session.sendMessage(data!)
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        signalingStateChanged stateChanged: RTCSignalingState) {
        print("PCO onSignalingStateChange: \(stateChanged)")
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        didOpenDataChannel dataChannel: RTCDataChannel!) {
        print("PCO didOpenDataChannel.")
    }
    
    func peerConnectionOnError(peerConnection: RTCPeerConnection!) {
        print("PCO onError.")
    }
    
    func peerConnectionOnRenegotiationNeeded(peerConnection: RTCPeerConnection!) {
        print("PCO onRenegotiationNeeded.")
        // TODO: Handle this
    }
}