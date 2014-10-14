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

class VideoConfig {
    var container: VideoLayoutParams
    var local: VideoLayoutParams?
    
    init(data: AnyObject) {
        let containerParams: AnyObject = data.objectForKey("containerParams")!
        let localParams: AnyObject? = data.objectForKey("local")
        
        self.container = VideoLayoutParams(data: containerParams)
        
        if localParams != nil {
            self.local = VideoLayoutParams(data: localParams!)
        }
    }
}

class VideoLayoutParams {
    var x, y, width, height: Int
    
    init(x: Int, y: Int, width: Int, height: Int) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }
    
    init(data: AnyObject) {
        let position: [AnyObject] = data.objectForKey("position")! as [AnyObject]
        self.x = position[0] as Int
        self.y = position[1] as Int
        
        let size: [AnyObject] = data.objectForKey("size")! as [AnyObject]
        self.width = size[0] as Int
        self.height = size[1] as Int
    }
}