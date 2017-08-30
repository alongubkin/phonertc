import Foundation
import AVFoundation
import AudioToolbox

@objc(PhoneRTCPlugin)
class PhoneRTCPlugin : CDVPlugin ,UIAlertViewDelegate{
var sessions: [String: Session]!
var peerConnectionFactory: RTCPeerConnectionFactory!

var videoConfig: VideoConfig?
var videoCapturer: RTCVideoCapturer?
var videoSource: RTCVideoSource?
var localVideoView: RTCEAGLVideoView?
var remoteVideoViews: [VideoTrackViewPair]!
var camera: String?
var scaleView:RTCEAGLVideoView?


var localVideoTrack: RTCVideoTrack?
var localAudioTrack: RTCAudioTrack?

func createSessionObject(_ command: CDVInvokedUrlCommand) {
    
    if let sessionKey = command.argument(at: 0) as? String {
        
        if let args: [String: AnyObject] = command.argument(at: 1) as! [String: AnyObject]? {
            
            let getIsInitiator: Bool = args["isInitiator"] as! Bool;
            let getTurn: [String:AnyObject] = args["turn"] as! [String:AnyObject];
            
            let getArgsStreams: [String:AnyObject] = args["streams"] as! [String:AnyObject];
            
            
            
            
            let config:SessionConfig = SessionConfig(isInitiator: getIsInitiator, turn: getTurn , streams:getArgsStreams );
                
            self.sessions[sessionKey] = Session(plugin: self,
                                                    peerConnectionFactory: peerConnectionFactory,
                                                    config: config,
                                                    callbackId: command.callbackId,
                                                    sessionKey: sessionKey )
            
            
            
            
        }
    }
}


func call(_ command: CDVInvokedUrlCommand) {
    let args: [String:AnyObject] = command.argument(at: 0 ) as! [String:AnyObject];
    if let sessionKey = args["sessionKey"] as? String {

        DispatchQueue.main.async() {
            if let session = self.sessions[sessionKey] {
                session.call()
            }
        }
    }
}

func receiveMessage(_ command: CDVInvokedUrlCommand) {
    let args: [String:AnyObject] = command.argument(at: 0 ) as! [String:AnyObject];
    
    if let sessionKey = args["sessionKey"] as? String {
        if let message = args["message"] as? String {
            if let session = self.sessions[sessionKey] {
                DispatchQueue.global().async() {
                    session.receiveMessage(message)
                }
            }
        }
    }
}

func renegotiate(_ command: CDVInvokedUrlCommand) {
    let args: [String:AnyObject] = command.argument(at: 0) as! [String:AnyObject];
    if let sessionKey = args["sessionKey"] as? String {
        if let config: SessionConfig = args["config"] as? SessionConfig {
            
            DispatchQueue.main.async() {
                if let session = self.sessions[sessionKey] {
                    session.config = config;
                    session.createOrUpdateStream()
                }
            }
        }
    }
}

func disconnect(_ command: CDVInvokedUrlCommand) {
    let args: [String: AnyObject] = command.argument(at: 0 ) as! [String: AnyObject];
    if let sessionKey = args["sessionKey"] as? String {
        
        DispatchQueue.global().async() {
            if (self.sessions[sessionKey] != nil) {
                self.sessions[sessionKey]!.disconnect(true)
            }
        }
    }
}


func sendMessage(_ callbackId: String, message: NSData) {
    let json = (try! JSONSerialization.jsonObject(with: message as Data,
        options: JSONSerialization.ReadingOptions.mutableLeaves)) as! NSDictionary
    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: json as [NSObject : AnyObject] )
    
    pluginResult?.setKeepCallbackAs(true);
    self.commandDelegate?.send(pluginResult, callbackId:callbackId)
    
}

func setVideoView(_ command: CDVInvokedUrlCommand) {
    let config: [String : AnyObject ] = command.argument(at: 0) as! [String : AnyObject ]
    
    DispatchQueue.main.async() {
        
        let videoConfig:VideoConfig = VideoConfig( data: config )
        if videoConfig.container.width == 0 || videoConfig.container.height == 0 {
            return
        }
        
        self.videoConfig = videoConfig
        self.camera = config["camera"] as? String
        
        if self.videoConfig!.local != nil {
            if self.localVideoTrack == nil {
                if(self.camera == "Front" || self.camera == "Back") {
                    self.initLocalVideoTrack(self.camera!)
                }else {
                    self.initLocalVideoTrack()
                }
            }
            
            if self.videoConfig!.local == nil {
                if self.localVideoView != nil {
                    self.localVideoView!.isHidden = true
                    self.localVideoView!.removeFromSuperview()
                    self.localVideoView = nil
                }
            } else {
                let params = self.videoConfig!.local!
                if self.localVideoView != nil {
                    self.localVideoView!.frame = CGRect( x:CGFloat(params.x + self.videoConfig!.container.x),
                                                         y:CGFloat(params.y + self.videoConfig!.container.y),
                                                         width:CGFloat(params.width),
                                                         height:CGFloat(params.height)
                                            )
                } else {
                    // otherwise, create the local video view
                    self.localVideoView = self.createVideoView(params)
                    self.localVideoTrack!.add(self.localVideoView!)
                }
            }
            
            self.refreshVideoContainer()
            
        }
        let gesture = UIPanGestureRecognizer(target:self, action:#selector(PhoneRTCPlugin.handlePanGesture(sender:)));
        self.localVideoView!.addGestureRecognizer(gesture)
    }
}

func handlePanGesture(sender: UIPanGestureRecognizer){
    let transX = sender.location(in: self.webView).x
    let transY = sender.location(in: self.webView).y
    print(transX,transY)
    sender.view?.center = CGPoint(x: transX, y:transY )
    
}

func hideVideoView(_ command: CDVInvokedUrlCommand) {
    DispatchQueue.main.async() {
        if (self.localVideoView != nil) {
            self.localVideoView!.isHidden = true;
        }
        
        for remoteVideoView in self.remoteVideoViews {
            remoteVideoView.videoView.isHidden = true;
        }
    }
}

func showVideoView(_ command: CDVInvokedUrlCommand) {
    DispatchQueue.main.async() {
        if (self.localVideoView != nil) {
            self.localVideoView!.isHidden = false;
        }
        
        for remoteVideoView in self.remoteVideoViews {
            remoteVideoView.videoView.isHidden = false;
        }
    }
}

func createVideoView(_ params: VideoLayoutParams? = nil) -> RTCEAGLVideoView {
    if params != nil {
        let frame = CGRect(x:
            CGFloat(params!.x + self.videoConfig!.container.x),
                           y:
            CGFloat(params!.y + self.videoConfig!.container.y),
                           width:
            CGFloat(params!.width),
                           height:
            CGFloat(params!.height)
        )
        
        scaleView = RTCEAGLVideoView(frame: frame)
    } else {
        scaleView = RTCEAGLVideoView()
        
        
        let buttonSpeaker = UIButton(frame: CGRect(x:
            CGFloat(self.videoConfig!.container.width-40),
                                                   y:
            CGFloat(self.videoConfig!.container.height-40),
                                                   width:
            CGFloat(30),
                                                   height:
            CGFloat(30)
            ))
        buttonSpeaker.addTarget(self, action: #selector(PhoneRTCPlugin.tapSpeakerBtn(sender:)), for: UIControlEvents.touchUpInside)

        
        DispatchQueue(label:"my_queue").async() {
            DispatchQueue.main.async() {
                try? AVAudioSession.sharedInstance().overrideOutputAudioPort(AVAudioSessionPortOverride.speaker);

            }
        }
    }

    self.webView!.addSubview(scaleView!)
    return scaleView!
}

func tapSpeakerBtn(sender:UIButton){
    if (sender.isSelected == true) {
        
        sender.isSelected=false
        SJProgressHUD.showInfo("扬声器已关闭", autoRemove:true)

        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(.none)
        
    }else{
        
        sender.isSelected=true
        SJProgressHUD.showInfo("扬声器已打开", autoRemove:true)
        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(.speaker)
        
    }
    
}



func initLocalAudioTrack() {
    
    localAudioTrack = peerConnectionFactory.audioTrack(withID: "ARDAMSa0")
    DispatchQueue(label:"my_queue").async() {
        DispatchQueue.main.async() {
            try? AVAudioSession.sharedInstance().overrideOutputAudioPort(AVAudioSessionPortOverride.speaker);
            
            
        }
    }
    
}

func initLocalVideoTrack() {
    var cameraID: String?
    for captureDevice in AVCaptureDevice.devices(withMediaType: AVMediaTypeVideo) {
        if (captureDevice as AnyObject).position == AVCaptureDevicePosition.front {
            cameraID = (captureDevice as AnyObject).localizedName
        }
    }
    
    self.videoCapturer = RTCVideoCapturer(deviceName: cameraID)
    self.videoSource = self.peerConnectionFactory.videoSource(
        with: self.videoCapturer,
        constraints: RTCMediaConstraints()
    )
    
    self.localVideoTrack = self.peerConnectionFactory
        .videoTrack(withID: "ARDAMSv0", source: self.videoSource)
    
}

func initLocalVideoTrack(_ camera: String) {
    NSLog("PhoneRTC: initLocalVideoTrack(camera: String) invoked")
    var cameraID: String?
    for captureDevice in AVCaptureDevice.devices(withMediaType: AVMediaTypeVideo) {
        if (captureDevice as AnyObject).position == AVCaptureDevicePosition.front {
            if camera == "Front"{
                cameraID = (captureDevice as AnyObject).localizedName
            }
        }
        if (captureDevice as AnyObject).position == AVCaptureDevicePosition.back {
            if camera == "Back"{
                cameraID = (captureDevice as AnyObject).localizedName
            }
        }
    }
    
    self.videoCapturer = RTCVideoCapturer(deviceName: cameraID)
    self.videoSource = self.peerConnectionFactory.videoSource(
        with: self.videoCapturer,
        constraints: RTCMediaConstraints()
    )
    
    self.localVideoTrack = self.peerConnectionFactory
        .videoTrack(withID: "ARDAMSv0", source: self.videoSource)
}

func addRemoteVideoTrack(_ videoTrack: RTCVideoTrack) {
    if self.videoConfig == nil {
        return
    }
    let videoView = createVideoView()
    
    videoTrack.add(videoView)
    self.remoteVideoViews.append(VideoTrackViewPair(videoView: videoView, videoTrack: videoTrack))
    
    refreshVideoContainer()
    
    if self.localVideoView != nil {
        self.webView!.addSubview(self.localVideoView!)
    }
    
}

func removeRemoteVideoTrack(_ videoTrack: RTCVideoTrack) {
    DispatchQueue.main.async() {
        
        if (self.localVideoView != nil) {
            self.localVideoView!.isHidden = true
            self.localVideoView!.removeFromSuperview()
            self.localVideoView = nil
        }
        for i in 0 ..< self.remoteVideoViews.count {
            let pair = self.remoteVideoViews[i]
            if pair.videoTrack == videoTrack {
                pair.videoView.isHidden = true
                pair.videoView.removeFromSuperview()
                
                self.remoteVideoViews.remove(at: i)
                self.refreshVideoContainer()
                return
            }
        }
    }
}

func refreshVideoContainer() {
    let n = self.remoteVideoViews.count
    
    if n == 0 {
        return
    }
    
    let rows = n < 9 ? 2 : 3
    let videosInRow = n == 2 ? 2 : Int(ceil(Float(n) / Float(rows)))
    
    let videoSize = Int(Float(self.videoConfig!.container.width) / Float(videosInRow))
    let actualRows = Int(ceil(Float(n) / Float(videosInRow)))
    
    var y = getCenter(actualRows, videoSize: videoSize, containerSize: self.videoConfig!.container.height)
        + self.videoConfig!.container.y
    
    var videoViewIndex = 0
    var row = 0
    
    while row < rows && videoViewIndex < n {
        
        var x = getCenter( row < row - 1 || n % rows == 0 ?
            videosInRow : n - (min(n, videoViewIndex + videosInRow) - 1),
                          videoSize: videoSize,
                          containerSize: self.videoConfig!.container.width)
            + self.videoConfig!.container.x
        
        var video = 0;
        while video < videosInRow && videoViewIndex < n {
            
            let pair = self.remoteVideoViews[videoViewIndex];
            videoViewIndex += 1;
            
            
            pair.videoView.frame = CGRect(x:
                CGFloat(x),
                                          y:
                CGFloat(y),
                                          width:
                CGFloat(videoSize),
                                          height:
                CGFloat(videoSize)
            )
            
            x += Int(videoSize)
            
            video += 1
        }
        
        y += Int(videoSize)

        
        row += 1
        
    }

    
}

func getCenter(_ videoCount: Int, videoSize: Int, containerSize: Int) -> Int {
    return lroundf(Float(containerSize - videoSize * videoCount) / 2.0)
}

func onSessionDisconnect(_ sessionKey: String) {
    self.sessions.removeValue(forKey: sessionKey)
    
    if self.sessions.count == 0 {
        
        DispatchQueue.main.sync() {
            if (self.localVideoView != nil) {
                self.localVideoView!.isHidden = true
                self.localVideoView!.removeFromSuperview()
                self.localVideoView = nil
            }
        }
        
        self.localVideoTrack = nil
        self.localAudioTrack = nil
        
        self.videoSource = nil
        self.videoCapturer = nil
        
    }
}

override func pluginInitialize() {
    self.sessions = [:];
    self.remoteVideoViews = [];
    
    peerConnectionFactory = RTCPeerConnectionFactory()
    RTCPeerConnectionFactory.initializeSSL()
    
}
}

struct VideoTrackViewPair {
var videoView: RTCEAGLVideoView
var videoTrack: RTCVideoTrack
}
