#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import "RTCEAGLVideoView.h"
#import "PhoneRTCDelegate.h"

@interface PhoneRTCPlugin : CDVPlugin<PhoneRTCProtocol>
@property(nonatomic, strong) PhoneRTCDelegate *webRTC;
@property(nonatomic, strong) NSString *sendMessageCallbackId;
@property(nonatomic, strong) RTCEAGLVideoView* localVideoView;
@property(nonatomic, strong) RTCEAGLVideoView* remoteVideoView;
@property(nonatomic, strong) RTCPeerConnectionFactory* factory;
- (void)call:(CDVInvokedUrlCommand*)command;
- (void)updateVideoPosition:(CDVInvokedUrlCommand*)command;
- (void)receiveMessage:(CDVInvokedUrlCommand*)command;
@end

@interface MessagesObserver
- (void)sendMessage:(NSString *)message;
@end
