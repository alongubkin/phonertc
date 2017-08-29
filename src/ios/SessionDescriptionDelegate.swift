import Foundation

class SessionDescriptionDelegate : UIResponder, RTCSessionDescriptionDelegate {
var session: Session

init(session: Session) {
    self.session = session
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    didCreateSessionDescription originalSdp: RTCSessionDescription!, error: Error!) {
    if error != nil {
        print("SDP OnFailure: \(error)")
        return
    }
        
    let sdp = RTCSessionDescription(
        type: originalSdp.type,
        sdp: self.session.preferISAC(originalSdp.description)
    )
    
    self.session.peerConnection.setLocalDescriptionWith(self, sessionDescription: sdp)
        
    DispatchQueue.main.async {
        let json: [String: String ] = [
            "type": sdp!.type,
            "sdp": sdp!.description
        ]
        
        let data: Data?
        do {
            data = try JSONSerialization.data(withJSONObject: json,
                            options: JSONSerialization.WritingOptions())
        } catch let error as NSError {
            print( "error: \(error)" );
            
            data = nil
        } catch {
            
            fatalError()
        }
        
        self.session.sendMessage(data!)
    }
}

func peerConnection(_ peerConnection: RTCPeerConnection!,
    didSetSessionDescriptionWithError error: Error!) {
    if error != nil {
        print("SDP OnFailure: \(error)")
        return
    }
        
    DispatchQueue.main.async {
        if self.session.config.isInitiator {
            if self.session.peerConnection.remoteDescription != nil {
                print("SDP onSuccess - drain candidates")
                self.drainRemoteCandidates()
            }
        } else {
            if self.session.peerConnection.localDescription != nil {
                self.drainRemoteCandidates()
            } else {
                self.session.peerConnection.createAnswer(with: self,
                    constraints: self.session.constraints)
            }
        }
    }
}

func drainRemoteCandidates() {
    if self.session.queuedRemoteCandidates != nil {
        for candidate in self.session.queuedRemoteCandidates! {
            self.session.peerConnection.add(candidate)
        }
        self.session.queuedRemoteCandidates = nil
    }
}
}
