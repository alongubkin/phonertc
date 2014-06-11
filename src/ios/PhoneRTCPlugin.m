#import "PhoneRTCPlugin.h"
#import <AVFoundation/AVFoundation.h>

@implementation PhoneRTCPlugin
@synthesize localVideoView;
@synthesize remoteVideoView;

- (void)call:(CDVInvokedUrlCommand*)command
{
    // TODO: Only add video frame if video is enabled
    // TODO: Configure the video frame accurately
    localVideoView = [[RTCEAGLVideoView alloc] initWithFrame:CGRectMake(50, 50, 300, 300)];
    localVideoView.hidden = YES;
    localVideoView.userInteractionEnabled = NO;
    [self.webView.superview addSubview:localVideoView];

    remoteVideoView = [[RTCEAGLVideoView alloc] initWithFrame:CGRectMake(50, 350, 300, 300)];
    remoteVideoView.hidden = YES;
    remoteVideoView.userInteractionEnabled = NO;
    [self.webView.superview addSubview:remoteVideoView];

    self.sendMessageCallbackId = command.callbackId;

    BOOL isInitator = [[command.arguments objectAtIndex:0] boolValue];
	NSString *turnServerHost = (NSString *)[command.arguments objectAtIndex:1];
	NSString *turnUsername = (NSString *)[command.arguments objectAtIndex:2];
	NSString *turnPassword = (NSString *)[command.arguments objectAtIndex:3];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [pluginResult setKeepCallbackAsBool:true];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT,
                                             (unsigned long)NULL), ^(void) {
        RTCICEServer *stunServer = [[RTCICEServer alloc]
                                    initWithURI:[NSURL URLWithString:@"stun:stun.l.google.com:19302"]
                                    username: @""
                                    password: @""];
        RTCICEServer *turnServer = [[RTCICEServer alloc]
                                    initWithURI:[NSURL URLWithString:turnServerHost]
                                    username: turnUsername
                                    password: turnPassword];
        
        self.webRTC = [[PhoneRTCDelegate alloc] init];
        self.webRTC.isInitiator = isInitator;

        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(sendMessage:) name:@"SendMessage" object:nil];
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(addLocalVideoTrack:) name:@"SendLocalVideoTrack" object:nil];
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(addRemoteVideoTrack:) name:@"SendRemoteVideoTrack" object:nil];

        [self.webRTC onICEServers:@[stunServer, turnServer]];
    });
}

- (void)receiveMessage:(CDVInvokedUrlCommand*)command
{
    NSString *message = [command.arguments objectAtIndex:0];
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT,
                                             (unsigned long)NULL), ^(void) {
        [self.webRTC receiveMessage:message];
    });
}

- (void)disconnect:(CDVInvokedUrlCommand*)command
{
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT,
                                             (unsigned long)NULL), ^(void) {
        [self.webRTC disconnect];
    });
}

- (void)sendMessage:(NSNotification *)notification {
	NSData *message = [notification object];
    NSDictionary *jsonObject=[NSJSONSerialization
                              JSONObjectWithData:message
                              options:NSJSONReadingMutableLeaves
                              error:nil];

    NSLog(@"SENDING MESSAGE: %@", [[NSString alloc] initWithData:message encoding:NSUTF8StringEncoding]);
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                  messageAsDictionary:jsonObject];
    [pluginResult setKeepCallbackAsBool:true];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.sendMessageCallbackId];
}

- (void)addLocalVideoTrack:(NSNotification *)notification {
    NSLog(@"addLocalStream 1");
    RTCVideoTrack* track = [notification object];
    localVideoView.videoTrack = track;
    localVideoView.hidden = NO;
    [self.webView.superview bringSubviewToFront:localVideoView];
}

- (void)addRemoteVideoTrack:(NSNotification *)notification {
    NSLog(@"addRemoteStream 1");
    RTCVideoTrack* track = [notification object];
    remoteVideoView.videoTrack = track;
    remoteVideoView.hidden = NO;
    [self.webView.superview bringSubviewToFront:remoteVideoView];
}

@end
