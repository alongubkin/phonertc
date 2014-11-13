import Foundation

class SessionDescriptionDelegate : UIResponder, RTCSessionDescriptionDelegate {
    var session: Session
    
    init(session: Session) {
        self.session = session
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        didCreateSessionDescription originalSdp: RTCSessionDescription!, error: NSError!) {
        if error != nil {
            println("SDP OnFailure: \(error)")
            return
        }
            
        var sdp = RTCSessionDescription(
            type: originalSdp.type,
            sdp: self.session.preferISAC(originalSdp.description)
        )
        
        self.session.peerConnection.setLocalDescriptionWithDelegate(self, sessionDescription: sdp)
            
        dispatch_async(dispatch_get_main_queue()) {
            var jsonError: NSError?
            
            let json: AnyObject = [
                "type": sdp.type,
                "sdp": sdp.description
            ]
            
            let data = NSJSONSerialization.dataWithJSONObject(json,
                options: NSJSONWritingOptions.allZeros,
                error: &jsonError)
            
            self.session.sendMessage(data!)
        }
    }
    
    func peerConnection(peerConnection: RTCPeerConnection!,
        didSetSessionDescriptionWithError error: NSError!) {
        if error != nil {
            println("SDP OnFailure: \(error)")
            return
        }
            
        dispatch_async(dispatch_get_main_queue()) {
            if self.session.config.isInitiator {
                if self.session.peerConnection.remoteDescription != nil {
                    println("SDP onSuccess - drain candidates")
                    self.drainRemoteCandidates()
                }
            } else {
                if self.session.peerConnection.localDescription != nil {
                    self.drainRemoteCandidates()
                } else {
                    self.session.peerConnection.createAnswerWithDelegate(self,
                        constraints: self.session.constraints)
                }
            }
        }
    }
    
    func drainRemoteCandidates() {
        for candidate in self.session.queuedRemoteCandidates! {
            self.session.peerConnection.addICECandidate(candidate)
        }
        
        self.session.queuedRemoteCandidates = nil
    }
}