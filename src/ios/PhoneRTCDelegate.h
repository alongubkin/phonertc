#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import <Cordova/CDVViewController.h>

#import "RTCICECandidate.h"
#import "RTCICEServer.h"
#import "RTCMediaConstraints.h"
#import "RTCMediaStream.h"
#import "RTCPair.h"
#import "RTCPeerConnection.h"
#import "RTCPeerConnectionDelegate.h"
#import "RTCPeerConnectionFactory.h"
#import "RTCSessionDescription.h"
#import "RTCVideoRenderer.h"
#import "RTCVideoCapturer.h"
#import "RTCVideoTrack.h"
#import "RTCSessionDescriptionDelegate.h"

@protocol PHONERTCSendMessage<NSObject>
- (void)sendMessage:(NSData*)message;
- (void)sendRemoteVideoTrack:(RTCVideoTrack*)track;
- (void)resetUi;
@end

@protocol PhoneRTCProtocol<NSObject>
- (void)addLocalVideoTrack:(RTCVideoTrack *)track;
- (void)addRemoteVideoTrack:(RTCVideoTrack *)track;
- (void)resetUi;
- (void)callComplete;
@end

@interface PCObserver : NSObject<RTCPeerConnectionDelegate>
- (id)initWithDelegate:(id<PHONERTCSendMessage>)delegate;
@end

@protocol ICEServerDelegate<NSObject>
- (void)onICEServers:(NSArray*)servers;
@end

@interface PhoneRTCDelegate : UIResponder<ICEServerDelegate,
                                        PHONERTCSendMessage,
                                        RTCSessionDescriptionDelegate>
@property(nonatomic, strong) PCObserver *pcObserver;
@property(nonatomic, strong) RTCPeerConnection *peerConnection;
@property(nonatomic, strong) RTCPeerConnectionFactory *peerConnectionFactory;
@property(nonatomic, strong) NSMutableArray *queuedRemoteCandidates;
@property(nonatomic, strong) RTCMediaConstraints *constraints;
@property(nonatomic, weak) id<PhoneRTCProtocol> delegate;
@property(nonatomic, strong) RTCVideoCapturer *capturer;
@property(assign) BOOL doVideo;
@property(assign) BOOL isInitiator;

+ (NSString *)preferISAC:(NSString *)origSDP;
- (id)initWithDelegate:(id)delegate andIsInitiator:(BOOL)isInitiator;
- (id)initWithDelegate:(id)delegate andIsInitiator:(BOOL)isInitiator andDoVideo:(BOOL)doVideo;
- (void)drainRemoteCandidates;

- (void)sendMessage:(NSData*)message;
- (void)receiveMessage:(NSString*)message;
- (void)disconnect;
@end
