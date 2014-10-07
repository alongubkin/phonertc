import Foundation

class SessionConfig {
    var isInitiator: Bool
    var turn: TurnConfig
    var streams: StreamsConfig
    
    init(data: AnyObject) {
        self.isInitiator = data.objectForKey("isInitiator") as Bool
        
        let turnObject: AnyObject = data.objectForKey("turn")!
        self.turn = TurnConfig(
            host: turnObject.objectForKey("host") as String,
            username: turnObject.objectForKey("username") as String,
            password: turnObject.objectForKey("password") as String
        )
        
        let streamsObject: AnyObject = data.objectForKey("streams")!
        self.streams = StreamsConfig(
            audio: streamsObject.objectForKey("audio") as Bool,
            video: streamsObject.objectForKey("video") as Bool
        )
    }
}

struct TurnConfig {
    var host: String
    var username: String
    var password: String
}

struct StreamsConfig {
    var audio: Bool
    var video: Bool
}