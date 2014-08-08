#import "PhoneRTCDelegate.h"

@implementation PhoneRTCDelegate

@synthesize pcObserver = _pcObserver;
@synthesize peerConnection = _peerConnection;
@synthesize peerConnectionFactory = _peerConnectionFactory;
@synthesize queuedRemoteCandidates = _queuedRemoteCandidates;

- (void)onICEServers:(NSArray*)servers
{
    self.queuedRemoteCandidates = [NSMutableArray array];
    self.peerConnectionFactory = [[RTCPeerConnectionFactory alloc] init];
    self.pcObserver = [[PCObserver alloc] initWithDelegate:self];
    [RTCPeerConnectionFactory initializeSSL];
    self.peerConnection =
    [self.peerConnectionFactory peerConnectionWithICEServers:servers
                                                 constraints:[self constraints]
                                                    delegate:self.pcObserver];

    RTCMediaStream *lms =
    [self.peerConnectionFactory mediaStreamWithLabel:@"ARDAMS"];

    // TODO: Make Camera Selectable
    if ([self doVideo]) {
        // Local capture copied from AppRTC
        NSString* cameraID = nil;
        for (AVCaptureDevice* captureDevice in
             [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo]) {
            // TODO: Make this camera option configurable
            if (captureDevice.position == AVCaptureDevicePositionFront) {
                cameraID = [captureDevice localizedName];
                break;
            }
        }
        NSAssert(cameraID, @"Unable to get the front camera id");
        RTCVideoSource* videoSource = [self.peerConnectionFactory
                            videoSourceWithCapturer:[RTCVideoCapturer capturerWithDeviceName:cameraID]
                            constraints:[[RTCMediaConstraints alloc] init]];
        RTCVideoTrack* localVideoTrack =
            [self.peerConnectionFactory videoTrackWithID:@"ARDAMSv0" source:videoSource];
        if (localVideoTrack) {
            [lms addVideoTrack:localVideoTrack];
            [self.delegate addLocalVideoTrack:localVideoTrack];
        }
    }
    [lms addAudioTrack:[self.peerConnectionFactory audioTrackWithID:@"ARDAMSa0"]];

    [self.peerConnection addStream:lms constraints:[self constraints]];

    // End local capture

    if ([self isInitiator]) {
        [self.peerConnection createOfferWithDelegate:self constraints:[self constraints]];
    }
}


- (void)peerConnection:(RTCPeerConnection *)peerConnection
didCreateSessionDescription:(RTCSessionDescription *)origSdp
                 error:(NSError *)error {
    if (error) {
        NSLog(@"SDP OnFailure: %@", error);
        NSAssert(NO, error.description);
        return;
    }
    
    RTCSessionDescription* sdp =
    [[RTCSessionDescription alloc]
     initWithType:origSdp.type
     sdp:[PhoneRTCDelegate preferISAC:origSdp.description]];
    [self.peerConnection setLocalDescriptionWithDelegate:self
                                          sessionDescription:sdp];

    dispatch_async(dispatch_get_main_queue(), ^(void) {
        NSDictionary *json = @{ @"type" : sdp.type, @"sdp" : sdp.description };
        NSError *error2;
        NSData *data =
        [NSJSONSerialization dataWithJSONObject:json options:0 error:&error2];
        NSAssert(!error,
                 @"%@",
                 [NSString stringWithFormat:@"Error: %@", error2.description]);
        [self sendMessage:data];
    });
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
didSetSessionDescriptionWithError:(NSError *)error {
    if (error) {
        NSAssert(NO, error.description);
        return;
    }

    dispatch_async(dispatch_get_main_queue(), ^(void) {
        if ([self isInitiator]) {
            if (self.peerConnection.remoteDescription) {
                NSLog(@"SDP onSuccess - drain candidates");
                [self drainRemoteCandidates];
            }
        } else {
            if (self.peerConnection.localDescription != nil) {
                [self drainRemoteCandidates];
            } else {
                [self.peerConnection createAnswerWithDelegate:self constraints:[self constraints]];
            }
        }
    
    });
}

+ (NSString *)firstMatch:(NSRegularExpression *)pattern
              withString:(NSString *)string {
    NSTextCheckingResult* result =
    [pattern firstMatchInString:string
                        options:0
                          range:NSMakeRange(0, [string length])];
    if (!result)
        return nil;
    return [string substringWithRange:[result rangeAtIndex:1]];
}

+ (NSString *)preferISAC:(NSString *)origSDP {
    int mLineIndex = -1;
    NSString* isac16kRtpMap = nil;
    origSDP = [origSDP stringByReplacingOccurrencesOfString:@"\r\n" withString:@"\n"];
    NSArray* lines = [origSDP componentsSeparatedByString:@"\n"];
    NSRegularExpression* isac16kRegex = [NSRegularExpression
                                         regularExpressionWithPattern:@"^a=rtpmap:(\\d+) ISAC/16000[\r]?$"
                                         options:0
                                         error:nil];
    for (int i = 0;
         (i < [lines count]) && (mLineIndex == -1 || isac16kRtpMap == nil);
         ++i) {
        NSString* line = [lines objectAtIndex:i];
        if ([line hasPrefix:@"m=audio "]) {
            mLineIndex = i;
            continue;
        }
        isac16kRtpMap = [self firstMatch:isac16kRegex withString:line];
    }
    if (mLineIndex == -1) {
        NSLog(@"No m=audio line, so can't prefer iSAC");
        return origSDP;
    }
    if (isac16kRtpMap == nil) {
        NSLog(@"No ISAC/16000 line, so can't prefer iSAC");
        return origSDP;
    }
    NSArray* origMLineParts =
    [[lines objectAtIndex:mLineIndex] componentsSeparatedByString:@" "];
    NSMutableArray* newMLine =
    [NSMutableArray arrayWithCapacity:[origMLineParts count]];
    int origPartIndex = 0;
    // Format is: m=<media> <port> <proto> <fmt> ...
    [newMLine addObject:[origMLineParts objectAtIndex:origPartIndex++]];
    [newMLine addObject:[origMLineParts objectAtIndex:origPartIndex++]];
    [newMLine addObject:[origMLineParts objectAtIndex:origPartIndex++]];
    [newMLine addObject:isac16kRtpMap];
    for (; origPartIndex < [origMLineParts count]; ++origPartIndex) {
        if ([isac16kRtpMap compare:[origMLineParts objectAtIndex:origPartIndex]]
            != NSOrderedSame) {
            [newMLine addObject:[origMLineParts objectAtIndex:origPartIndex]];
        }
    }
    NSMutableArray* newLines = [NSMutableArray arrayWithCapacity:[lines count]];
    [newLines addObjectsFromArray:lines];
    [newLines replaceObjectAtIndex:mLineIndex
                        withObject:[newMLine componentsJoinedByString:@" "]];
    return [newLines componentsJoinedByString:@"\r\n"];
}

- (void)disconnect {
    [self.delegate resetUi];
    [self sendMessage:[@"{\"type\": \"bye\"}" dataUsingEncoding:NSUTF8StringEncoding]];
    [self.peerConnection close];
    self.peerConnection = nil;
    self.pcObserver = nil;
    self.constraints = nil;
    [RTCPeerConnectionFactory deinitializeSSL];
    self.peerConnectionFactory = nil;

    [self sendMessage:[@"{\"type\": \"__disconnected\"}" dataUsingEncoding:NSUTF8StringEncoding]];
    [self.delegate callComplete];
}

- (void)drainRemoteCandidates {
    for (RTCICECandidate *candidate in self.queuedRemoteCandidates) {
        [self.peerConnection addICECandidate:candidate];
    }
    self.queuedRemoteCandidates = nil;
}

- (void)sendMessage:(NSData *)message
{
    NSLog(@"sendMessage 1");
    [[NSNotificationCenter defaultCenter] postNotificationName:@"SendMessage" object:message];
}

- (void)sendRemoteVideoTrack:(RTCVideoTrack* )track
{
    [self.delegate addRemoteVideoTrack:track];
}

- (void)receiveMessage:(NSString *)message
{
    NSError *error;
    NSDictionary *objects = [NSJSONSerialization
                             JSONObjectWithData:[message dataUsingEncoding:NSUTF8StringEncoding]
                             options:0
                             error:&error];
    // NSAssert(!error,
    //          @"%@",
    //          [NSString stringWithFormat:@"Error: %@", error.description]);
    // NSAssert([objects count] > 0, @"Invalid JSON object");
    
    NSString *value = [objects objectForKey:@"type"];
    if ([value compare:@"candidate"] == NSOrderedSame) {
        NSString *mid = [objects objectForKey:@"id"];
        NSNumber *sdpLineIndex = [objects objectForKey:@"label"];
        NSString *sdp = [objects objectForKey:@"candidate"];
        RTCICECandidate *candidate =
        [[RTCICECandidate alloc] initWithMid:mid
                                       index:sdpLineIndex.intValue
                                         sdp:sdp];
        if (self.queuedRemoteCandidates) {
            [self.queuedRemoteCandidates addObject:candidate];
        } else {
            [self.peerConnection addICECandidate:candidate];
        }
    } else if (([value compare:@"offer"] == NSOrderedSame) ||
               ([value compare:@"answer"] == NSOrderedSame)) {
        NSString *sdpString = [objects objectForKey:@"sdp"];
        RTCSessionDescription *sdp = [[RTCSessionDescription alloc]
                                      initWithType:value sdp:[PhoneRTCDelegate preferISAC:sdpString]];
        [self.peerConnection setRemoteDescriptionWithDelegate:self sessionDescription:sdp];
    } else if ([value compare:@"bye"] == NSOrderedSame) {
        [self disconnect];
    } else {
//        NSAssert(NO, @"Invalid message: %@", message);
    }
}

@end


@implementation PCObserver {
    id<PHONERTCSendMessage> _delegate;
}

- (id)initWithDelegate:(id<PHONERTCSendMessage>)delegate {
    if (self = [super init]) {
        _delegate = delegate;
    }
    return self;
}

- (void)peerConnectionOnError:(RTCPeerConnection *)peerConnection {
    NSLog(@"PCO onError.");
    NSAssert(NO, @"PeerConnection failed.");
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
 signalingStateChanged:(RTCSignalingState)stateChanged {
    NSLog(@"PCO onSignalingStateChange: %d", stateChanged);
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
           addedStream:(RTCMediaStream *)stream {
    NSLog(@"PCO onAddStream.");
    dispatch_async(dispatch_get_main_queue(), ^(void) {
        NSAssert([stream.audioTracks count] >= 1,
                 @"Expected at least 1 audio stream");
        if ([stream.videoTracks count] > 0) {
            [_delegate sendRemoteVideoTrack:stream.videoTracks[0]];
        }
    });
    [_delegate sendMessage:[@"{\"type\": \"__answered\"}" dataUsingEncoding:NSUTF8StringEncoding]];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
         removedStream:(RTCMediaStream *)stream {
    NSLog(@"PCO onRemoveStream.");
    [_delegate resetUi];
}

- (void)
peerConnectionOnRenegotiationNeeded:(RTCPeerConnection *)peerConnection {
    NSLog(@"PCO onRenegotiationNeeded.");
    // TODO(hughv): Handle this.
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
       gotICECandidate:(RTCICECandidate *)candidate {
    NSLog(@"PCO onICECandidate.\n  Mid[%@] Index[%d] Sdp[%@]",
          candidate.sdpMid,
          candidate.sdpMLineIndex,
          candidate.sdp);
    NSDictionary *json =
    @{ @"type" : @"candidate",
       @"label" : [NSNumber numberWithInt:candidate.sdpMLineIndex],
       @"id" : candidate.sdpMid,
       @"candidate" : candidate.sdp };
    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:json options:0 error:&error];
    if (!error) {
        NSLog(@"gotICECandidate -- sending message");
        [_delegate sendMessage:data];
    } else {
        NSAssert(NO, @"Unable to serialize JSON object with error: %@",
                 error.localizedDescription);
    }
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
   iceGatheringChanged:(RTCICEGatheringState)newState {
    NSLog(@"PCO onIceGatheringChange. %d", newState);
    NSString *stateString;
    switch(newState) {
        case RTCICEGatheringNew:
            stateString = @"NEW";
            break;
        case RTCICEGatheringGathering:
            stateString = @"GATHERING";
            break;
        case RTCICEGatheringComplete:
            stateString = @"COMPLETE";
            break;
    }
    NSDictionary *json =
    @{ @"type" : @"IceGatheringChange",
       @"state" : stateString
       };
    NSError *error;
    [_delegate sendMessage:[NSJSONSerialization dataWithJSONObject:json options:0 error:&error]];
}

- (void)peerConnection:(RTCPeerConnection *)peerConnection
  iceConnectionChanged:(RTCICEConnectionState)newState {
    NSLog(@"PCO onIceConnectionChange. %d", newState);
    NSAssert(newState != RTCICEConnectionFailed, @"ICE Connection failed!");
}

@end
