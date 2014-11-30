import Foundation

class Session {
    var plugin: PhoneRTCPlugin
    var config: SessionConfig
    var constraints: RTCMediaConstraints
    var peerConnection: RTCPeerConnection!
    var pcObserver: PCObserver!
    var queuedRemoteCandidates: [RTCICECandidate]?
    var peerConnectionFactory: RTCPeerConnectionFactory
    var callbackId: String
    var stream: RTCMediaStream?
    var videoTrack: RTCVideoTrack?
    var sessionKey: String
    
    init(plugin: PhoneRTCPlugin,
         peerConnectionFactory: RTCPeerConnectionFactory,
         config: SessionConfig,
         callbackId: String,
         sessionKey: String) {
        self.plugin = plugin
        self.queuedRemoteCandidates = []
        self.config = config
        self.peerConnectionFactory = peerConnectionFactory
        self.callbackId = callbackId
        self.sessionKey = sessionKey
            
        // initialize basic media constraints
        self.constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                RTCPair(key: "OfferToReceiveAudio", value: "true"),
                RTCPair(key: "OfferToReceiveVideo", value:
                    self.plugin.videoConfig == nil ? "false" : "true"),
            ],
            
            optionalConstraints: [
                RTCPair(key: "internalSctpDataChannels", value: "true"),
                RTCPair(key: "DtlsSrtpKeyAgreement", value: "true")
            ]
        )
    }
    
    func call() {
        // create a list of ICE servers
        var iceServers: [RTCICEServer] = []
        iceServers.append(RTCICEServer(
            URI: NSURL(string: "stun:stun.l.google.com:19302"),
            username: "",
            password: ""))
        
        iceServers.append(RTCICEServer(
            URI: NSURL(string: self.config.turn.host),
            username: self.config.turn.username,
            password: self.config.turn.password))
        
        // initialize a PeerConnection
        self.pcObserver = PCObserver(session: self)
        self.peerConnection =
            peerConnectionFactory.peerConnectionWithICEServers(iceServers,
                constraints: self.constraints,
                delegate: self.pcObserver)
        
        // create a media stream and add audio and/or video tracks
        createOrUpdateStream()
        
        // create offer if initiator
        if self.config.isInitiator {
            self.peerConnection.createOfferWithDelegate(SessionDescriptionDelegate(session: self),
                constraints: constraints)
        }
    }
    
    func createOrUpdateStream() {
        if self.stream != nil {
            self.peerConnection.removeStream(self.stream)
            self.stream = nil
        }
        
        self.stream = peerConnectionFactory.mediaStreamWithLabel("ARDAMS")
        
        if self.config.streams.audio {
            // init local audio track if needed
            if self.plugin.localAudioTrack == nil {
                self.plugin.initLocalAudioTrack()
            }
            
            self.stream!.addAudioTrack(self.plugin.localAudioTrack!)
        }
        
        if self.config.streams.video {
            // init local video track if needed
            if self.plugin.localVideoTrack == nil {
                self.plugin.initLocalVideoTrack()
            }
            
            self.stream!.addVideoTrack(self.plugin.localVideoTrack!)
        }
        
        self.peerConnection.addStream(self.stream)
    }
    
    func receiveMessage(message: String) {
        // Parse the incoming JSON message.
        var error : NSError?
        let data : AnyObject? = NSJSONSerialization.JSONObjectWithData(
            message.dataUsingEncoding(NSUTF8StringEncoding)!,
            options: NSJSONReadingOptions.allZeros,
            error: &error)
        if let object: AnyObject = data {
            // Log the message to console.
            println("Received Message: \(object)")
            // If the message has a type try to handle it.
            if let type = object.objectForKey("type") as? String {
                switch type {
                case "candidate":
                    let mid: String = data?.objectForKey("id") as NSString
                    let sdpLineIndex: Int = (data?.objectForKey("label") as NSNumber).integerValue
                    let sdp: String = data?.objectForKey("candidate") as NSString
                    
                    let candidate = RTCICECandidate(
                        mid: mid,
                        index: sdpLineIndex,
                        sdp: sdp
                    )
                    
                    if self.queuedRemoteCandidates != nil {
                        self.queuedRemoteCandidates?.append(candidate)
                    } else {
                        self.peerConnection.addICECandidate(candidate)
                    }
                    
                    case "offer", "answer":
                        if let sdpString = object.objectForKey("sdp") as? String {
                            let sdp = RTCSessionDescription(type: type, sdp: self.preferISAC(sdpString))
                            self.peerConnection.setRemoteDescriptionWithDelegate(SessionDescriptionDelegate(session: self),
                                                                                 sessionDescription: sdp)
                        }
                    case "bye":
                        self.disconnect(false)
                    default:
                        println("Invalid message \(message)")
                }
            }
        } else {
            // If there was an error parsing then print it to console.
            if let parseError = error {
                println("There was an error parsing the client message: \(parseError.localizedDescription)")
            }
            // If there is no data then exit.
            return
        }
    }

    func disconnect(sendByeMessage: Bool) {
        if self.videoTrack != nil {
            self.removeVideoTrack(self.videoTrack!)
        }
        
        if self.peerConnection != nil {
            if sendByeMessage {
                let json: AnyObject = [
                    "type": "bye"
                ]
            
                let data = NSJSONSerialization.dataWithJSONObject(json,
                    options: NSJSONWritingOptions.allZeros,
                    error: nil)
            
                self.sendMessage(data!)
            }
        
            self.peerConnection.close()
            self.peerConnection = nil
            self.queuedRemoteCandidates = nil
        }
        
        let json: AnyObject = [
            "type": "__disconnected"
        ]
        
        let data = NSJSONSerialization.dataWithJSONObject(json,
            options: NSJSONWritingOptions.allZeros,
            error: nil)
        
        self.sendMessage(data!)
        
        self.plugin.onSessionDisconnect(self.sessionKey)
    }
    
    func addVideoTrack(videoTrack: RTCVideoTrack) {
        self.videoTrack = videoTrack
        self.plugin.addRemoteVideoTrack(videoTrack)
    }
    
    func removeVideoTrack(videoTrack: RTCVideoTrack) {
        self.plugin.removeRemoteVideoTrack(videoTrack)
    }
    
    func preferISAC(sdpDescription: String) -> String {
        var mLineIndex = -1
        var isac16kRtpMap: String?
        
        let origSDP = sdpDescription.stringByReplacingOccurrencesOfString("\r\n", withString: "\n")
        var lines = origSDP.componentsSeparatedByString("\n")
        let isac16kRegex = NSRegularExpression(
            pattern: "^a=rtpmap:(\\d+) ISAC/16000[\r]?$",
            options: NSRegularExpressionOptions.allZeros,
            error: nil)
        
        for var i = 0;
            (i < lines.count) && (mLineIndex == -1 || isac16kRtpMap == nil);
            ++i {
            let line = lines[i]
            if line.hasPrefix("m=audio ") {
                mLineIndex = i
                continue
            }
                
            isac16kRtpMap = self.firstMatch(isac16kRegex!, string: line)
        }
        
        if mLineIndex == -1 {
            println("No m=audio line, so can't prefer iSAC")
            return origSDP
        }
        
        if isac16kRtpMap == nil {
            println("No ISAC/16000 line, so can't prefer iSAC")
            return origSDP
        }
        
        let origMLineParts = lines[mLineIndex].componentsSeparatedByString(" ")

        var newMLine: [String] = []
        var origPartIndex = 0;
        
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++])
        newMLine.append(origMLineParts[origPartIndex++])
        newMLine.append(origMLineParts[origPartIndex++])
        newMLine.append(isac16kRtpMap!)
        
        for ; origPartIndex < origMLineParts.count; ++origPartIndex {
            if isac16kRtpMap != origMLineParts[origPartIndex] {
                newMLine.append(origMLineParts[origPartIndex])
            }
        }
        
        lines[mLineIndex] = " ".join(newMLine)
        return "\r\n".join(lines)
    }
    
    func firstMatch(pattern: NSRegularExpression, string: String) -> String? {
        var nsString = string as NSString
        
        let result = pattern.firstMatchInString(string,
            options: NSMatchingOptions.allZeros,
            range: NSMakeRange(0, nsString.length))
        
        if result == nil {
            return nil
        }
        
        return nsString.substringWithRange(result!.rangeAtIndex(1))
    }
    
    func sendMessage(message: NSData) {
        self.plugin.sendMessage(self.callbackId, message: message)
    }
}

