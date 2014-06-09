#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import "RTCEAGLVideoView.h"
#include "PhoneRTCDelegate.h"

@interface PhoneRTCPlugin : CDVPlugin
@property(nonatomic, strong) PhoneRTCDelegate *webRTC;
@property(nonatomic, strong) NSString *sendMessageCallbackId;
@property(nonatomic, strong) RTCEAGLVideoView* localVideoView;
- (void)call:(CDVInvokedUrlCommand*)command;
- (void)receiveMessage:(CDVInvokedUrlCommand*)command;
@end

@interface MessagesObserver
- (void)sendMessage:(NSString *)message;
@end
