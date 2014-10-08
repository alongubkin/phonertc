import Foundation
import AVFoundation

@objc(PhoneRTCPlugin)
class PhoneRTCPlugin : CDVPlugin {
    var session: Session? // TODO: Map<String, Session>
    var peerConnectionFactory: RTCPeerConnectionFactory
    var callbackId : String?
    
    var videoConfig: VideoConfig?
    var videoCapturer: RTCVideoCapturer?
    var videoSource: RTCVideoSource?
    var localVideoTrack: RTCVideoTrack?
    var localVideoView: RTCEAGLVideoView?
    var remoteVideoViews: [RTCEAGLVideoView]
    
    override init(webView: UIWebView) {
        remoteVideoViews = []

        peerConnectionFactory = RTCPeerConnectionFactory()
        RTCPeerConnectionFactory.initializeSSL()
        
        super.init(webView: webView)
    }
    
    func createSessionObject(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        
        // create session config from the JS params
        let config = SessionConfig(data: command.arguments[0])
        
        // make sure the OK callback is permanent as we
        // use it to send messages to the JS
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        pluginResult.setKeepCallbackAsBool(true);
        commandDelegate.sendPluginResult(pluginResult, callbackId:command.callbackId)
        
        // create a session object and initialize it
        session = Session(
            plugin: self,
            peerConnectionFactory: peerConnectionFactory,
            config: config
        )
    }
    
    func call(comamnd: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_main_queue()) {
            self.session!.call()
        }
    }
    
    func receiveMessage(command: CDVInvokedUrlCommand) {
        let message = command.arguments[0] as String
        dispatch_async(dispatch_get_main_queue()) {
            self.session!.receiveMessage(message)
        }
    }
    
    func setVideoView(command: CDVInvokedUrlCommand) {
        let config: AnyObject = command.arguments[0]
        
        dispatch_async(dispatch_get_main_queue()) {
            // create session config from the JS params
            let videoConfig = VideoConfig(data: config)
            
            // make sure that it's not junk
            if videoConfig.container.width == 0 || videoConfig.container.height == 0 {
                return
            }
            
            self.videoConfig = videoConfig
            
            // add local video view
            if self.videoConfig!.local != nil {
                if self.localVideoTrack == nil {
                    self.initLocalVideoTrack()
                }
                
                if self.videoConfig!.local == nil {
                    // remove the local video view if it exists and
                    // the new config doesn't have the `local` property
                    if self.localVideoView != nil {
                        self.localVideoView!.removeFromSuperview()
                        self.localVideoView = nil
                    }
                } else {
                    let params = self.videoConfig!.local!
                    
                    // if the local video view already exists, just
                    // change its position according to the new config.
                    if self.localVideoView != nil {
                        self.localVideoView!.frame = CGRectMake(
                            CGFloat(params.x + self.videoConfig!.container.x),
                            CGFloat(params.y + self.videoConfig!.container.y),
                            CGFloat(params.width),
                            CGFloat(params.height)
                        )
                    } else {
                        // otherwise, create the local video view
                        self.localVideoView = self.createVideoView(params: params)
                        self.localVideoView?.videoTrack = self.localVideoTrack!
                    }
                }
                
                self.refreshVideoContainer()
            }
        }
    }
    
    func sendMessage(message: NSData) {
        let json = NSJSONSerialization.JSONObjectWithData(message,
            options: NSJSONReadingOptions.MutableLeaves,
            error: nil) as NSDictionary
        
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: json)
        pluginResult.setKeepCallbackAsBool(true);
        
        self.commandDelegate.sendPluginResult(pluginResult, callbackId:self.callbackId)
    }
    
    func createVideoView(params: VideoLayoutParams? = nil) -> RTCEAGLVideoView {
        var view: RTCEAGLVideoView
        
        if params != nil {
            let frame = CGRectMake(
                CGFloat(params!.x + self.videoConfig!.container.x),
                CGFloat(params!.y + self.videoConfig!.container.y),
                CGFloat(params!.width),
                CGFloat(params!.height)
            )
            
            view = RTCEAGLVideoView(frame: frame)
        } else {
            view = RTCEAGLVideoView()
        }
        
        view.userInteractionEnabled = false
        
        self.webView.addSubview(view)
        self.webView.bringSubviewToFront(view)
        
        return view
    }
    
    func initLocalVideoTrack() {
        var cameraID: String?
        for captureDevice in AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo) {
            // TODO: Make this camera option configurable
            if captureDevice.position == AVCaptureDevicePosition.Front {
                cameraID = captureDevice.localizedName
            }
        }
        
        self.videoCapturer = RTCVideoCapturer(deviceName: cameraID)
        self.videoSource = self.peerConnectionFactory.videoSourceWithCapturer(
            self.videoCapturer,
            constraints: RTCMediaConstraints()
        )
    
        self.localVideoTrack = self.peerConnectionFactory
            .videoTrackWithID("ARDAMSv0", source: self.videoSource)
    }
    
    func addRemoteVideoTrack(videoTrack: RTCVideoTrack) {
        if self.videoConfig == nil {
            return
        }
        
        // add a video view without position/size as it will get
        // resized and re-positioned in refreshVideoContainer
        let videoView = createVideoView()
        
        videoView.videoTrack = videoTrack
        self.remoteVideoViews.append(videoView)
        
        refreshVideoContainer()
        
        if self.localVideoView != nil {
            self.webView.bringSubviewToFront(self.localVideoView!)
        }
    }
    
    func refreshVideoContainer() {
        let n = self.remoteVideoViews.count
        
        if n == 0 {
            return
        }
        
        let totalArea = self.videoConfig!.container.width * self.videoConfig!.container.height
        let videoSize = sqrt(Float(totalArea) / Float(n))
        
        let videosInRow = Int(Float(self.videoConfig!.container.width) / videoSize)
        let rows = Int(ceil(Float(n) / Float(videosInRow)))
        
        var x = self.videoConfig!.container.x
        var y = self.videoConfig!.container.y
        
        var videoViewIndex = 0
        
        for var row = 0; row < rows; row++ {
            for var video = 0; video < videosInRow; video++ {
                let videoView = self.remoteVideoViews[videoViewIndex++]
                videoView.frame = CGRectMake(
                    CGFloat(x),
                    CGFloat(y),
                    CGFloat(videoSize),
                    CGFloat(videoSize)
                )

                x += Int(videoSize)
            }
            
            y += Int(videoSize)
        }
    }
}