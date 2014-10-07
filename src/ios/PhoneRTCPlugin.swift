import Foundation

class PhoneRTCPlugin : CDVPlugin {
    var session: Session? // TODO: Map<String, Session>
    var peerConnectionFactory: RTCPeerConnectionFactory
    var callbackId : String?
    
    override init(webView: UIWebView) {
        peerConnectionFactory = RTCPeerConnectionFactory()
        RTCPeerConnectionFactory.initializeSSL()
        
        super.init(webView: webView)
    }
    
    func createSessionObject(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        
        // create session config from the JS params
        let config = SessionConfig(
            isInitiator: command.arguments[0] as Bool,
            turn: TurnConfig(
                host: command.arguments[1] as String,
                username: command.arguments[2] as String,
                password: command.arguments[3] as String
            )
        )
        
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
    
    func receiveMessage(command: CDVInvokedUrlCommand) {
        let message = command.arguments[0] as String
        dispatch_async(dispatch_get_main_queue()) {
            self.session!.receiveMessage(message)
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
}