#import "PhoneRTCPlugin.h"
#import <AVFoundation/AVFoundation.h>

@implementation PhoneRTCPlugin
@synthesize localVideoView;
@synthesize remoteVideoView;

- (void)call:(CDVInvokedUrlCommand*)command
{
    self.sendMessageCallbackId = command.callbackId;

    BOOL isInitator = [[command.arguments objectAtIndex:0] boolValue];
	NSString *turnServerHost = (NSString *)[command.arguments objectAtIndex:1];
	NSString *turnUsername = (NSString *)[command.arguments objectAtIndex:2];
	NSString *turnPassword = (NSString *)[command.arguments objectAtIndex:3];
    BOOL doVideo = false;
    if ([command.arguments count] > 4 && [command.arguments objectAtIndex:4] != [NSNull null]) {
        NSDictionary *localVideo = [[command.arguments objectAtIndex:4] objectForKey:@"localVideo"];
        NSDictionary *remoteVideo = [[command.arguments objectAtIndex:4] objectForKey:@"remoteVideo"];
        localVideoView = [[RTCEAGLVideoView alloc] initWithFrame:CGRectMake([[localVideo objectForKey:@"x"] intValue], [[localVideo objectForKey:@"y"] intValue], [[localVideo objectForKey:@"width"] intValue], [[localVideo objectForKey:@"height"] intValue])];
        localVideoView.hidden = YES;
        localVideoView.userInteractionEnabled = NO;
        [self.webView.superview addSubview:localVideoView];

        remoteVideoView = [[RTCEAGLVideoView alloc] initWithFrame:CGRectMake([[remoteVideo objectForKey:@"x"] intValue], [[remoteVideo objectForKey:@"y"] intValue], [[remoteVideo objectForKey:@"width"] intValue], [[remoteVideo objectForKey:@"height"] intValue])];
        remoteVideoView.hidden = YES;
        remoteVideoView.userInteractionEnabled = NO;
        [self.webView.superview addSubview:remoteVideoView];

        doVideo = true;
    }
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [pluginResult setKeepCallbackAsBool:true];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(sendMessage:) name:@"SendMessage" object:nil];
    RTCICEServer *stunServer = [[RTCICEServer alloc]
                                initWithURI:[NSURL URLWithString:@"stun:stun.l.google.com:19302"]
                                username: @""
                                password: @""];
    RTCICEServer *turnServer = [[RTCICEServer alloc]
                                initWithURI:[NSURL URLWithString:turnServerHost]
                                username: turnUsername
                                password: turnPassword];
    // TODO: PhoneRTCDelegate should take constructor arguments
    self.webRTC = [[PhoneRTCDelegate alloc] init];
    self.webRTC.delegate = self;
    self.webRTC.isInitiator = isInitator;
    self.webRTC.doVideo = doVideo;
    self.webRTC.constraints = [[RTCMediaConstraints alloc]
       initWithMandatoryConstraints:
            @[
                 [[RTCPair alloc] initWithKey:@"OfferToReceiveAudio" value:@"true"],
                 [[RTCPair alloc] initWithKey:@"OfferToReceiveVideo" value:(doVideo ? @"true" : @"false")]
             ]
        optionalConstraints:
            @[
                 [[RTCPair alloc] initWithKey:@"internalSctpDataChannels" value:@"true"],
                 [[RTCPair alloc] initWithKey:@"DtlsSrtpKeyAgreement" value:@"true"]
             ]
    ];
    [self.webRTC onICEServers:@[stunServer, turnServer]];
}

- (void)updateVideoPosition:(CDVInvokedUrlCommand*)command
{
    // This will update the position of the video elements when the page moves
    NSDictionary *localVideo = [[command.arguments objectAtIndex:0] objectForKey:@"localVideo"];
    NSDictionary *remoteVideo = [[command.arguments objectAtIndex:0] objectForKey:@"remoteVideo"];
    localVideoView.frame = CGRectMake([[localVideo objectForKey:@"x"] intValue], [[localVideo objectForKey:@"y"] intValue], [[localVideo objectForKey:@"width"] intValue], [[localVideo objectForKey:@"height"] intValue]);
    remoteVideoView.frame = CGRectMake([[remoteVideo objectForKey:@"x"] intValue], [[remoteVideo objectForKey:@"y"] intValue], [[remoteVideo objectForKey:@"width"] intValue], [[remoteVideo objectForKey:@"height"] intValue]);
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

- (void)addLocalVideoTrack:(RTCVideoTrack *)track {
    NSLog(@"addLocalStream 1");
    localVideoView.videoTrack = track;
    localVideoView.hidden = NO;
    [self.webView.superview bringSubviewToFront:localVideoView];
}

- (void)addRemoteVideoTrack:(RTCVideoTrack *)track {
    NSLog(@"addRemoteStream 1");
    remoteVideoView.videoTrack = track;
    remoteVideoView.hidden = NO;
    [self.webView.superview bringSubviewToFront:remoteVideoView];
    [self.webView.superview bringSubviewToFront:localVideoView];
}

- (void)resetUi {
    NSLog(@"Reset Ui");
    self.localVideoView.videoTrack = nil;
    self.remoteVideoView.videoTrack = nil;
    localVideoView.hidden = YES;
    [localVideoView removeFromSuperview];
    remoteVideoView.hidden = YES;
    [remoteVideoView removeFromSuperview];
    localVideoView = nil;
    remoteVideoView = nil;
}

- (void)callComplete {
    NSLog(@"Call Complete");
    self.webRTC.delegate = nil;
    self.webRTC = nil;
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end
