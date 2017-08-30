import Foundation

struct TurnConfig {
var host: String
var username: String
var password: String

// ivan - write - 7/03
init(){
    self.host = "";
    self.username = "";
    self.password = "";
}

// ivan - write - 7/03
init( host:String, username:String, password:String ){
    self.host = host;
    self.username = username;
    self.password = password;
}
}

struct StreamsConfig {
var audio: Bool
var video: Bool

// ivan - write - 7/03
init() {
    self.audio = true;
    self.video = true;
}

// ivan - write - 7/03
init( audio:Bool, video:Bool ){
    self.audio = audio;
    self.video = video;
}
}

class SessionConfig {
var isInitiator: Bool
var turn: TurnConfig
var streams: StreamsConfig

// ivan - write - 7/03
init(isInitiator:Bool, turn:TurnConfig, streams:StreamsConfig ){
    self.isInitiator = isInitiator;
    self.turn = TurnConfig( host: turn.host, username: turn.username, password: turn.password  );
    self.streams = StreamsConfig( audio: streams.audio, video: streams.video );
}

init( isInitiator:Bool, turn:[String: AnyObject], streams:[String: AnyObject] ){
    self.isInitiator = isInitiator;
    
    self.turn = TurnConfig( host: (turn["host"] as? String)!, username: (turn["username"] as? String)!, password: (turn["password"] as? String)!  );
    
    self.streams = StreamsConfig( audio: streams["audio"] as! Bool, video: streams["video"] as! Bool );
    
}

init(_ data: [String: AnyObject] ) {
    self.isInitiator = data["isInitiator"] as! Bool
    
    let turnObject: [String: AnyObject] = data["turn"] as! [String: AnyObject]
    self.turn = TurnConfig(
        host: turnObject["host"] as! String,
        username: turnObject["username"] as! String,
        password: turnObject["password"] as! String
    )

    let streamsObject: [String: AnyObject] = data["streams"] as! [String: AnyObject];
    self.streams = StreamsConfig(
        audio: streamsObject["audio"] as! Bool,
        video: streamsObject["video"] as! Bool
    )
}
}

class VideoConfig {
var container: VideoLayoutParams
var local: VideoLayoutParams?

// ivan - write - 7/03
init( container:VideoLayoutParams, local:VideoLayoutParams? ) {
    
    self.container = VideoLayoutParams( x:container.x, y:container.y, width: container.width, height: container.height );
    
    if local != nil {
        self.local = VideoLayoutParams( x:local!.x, y:local!.y, width: local!.width, height: local!.height );
    }
    
}

// ivan -- write - 7/7
init( data: [String : AnyObject]) {
    let containerParams: [String : AnyObject ] = data["containerParams"] as! [String : AnyObject ]
    let localParams: [String : AnyObject ]? = data["local"] as? [String : AnyObject ]
    
    self.container = VideoLayoutParams( containerParams )
    
    if localParams != nil {
        self.local = VideoLayoutParams( localParams! )
    }
}

// ivan -- write - 7/7
init(_ data: [String : AnyObject]) {
    let containerParams: [String : AnyObject ] = data["containerParams"] as! [String : AnyObject ]
    let localParams: [String : AnyObject ]? = data["local"] as? [String : AnyObject ]
    
    self.container = VideoLayoutParams( containerParams )
    
    if localParams != nil {
        self.local = VideoLayoutParams( localParams! )
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

// ivan -- write - 7/7
init(_ data: [String : AnyObject ]) {
    let position: [Int] = data["position"] as! [Int]
    self.x = position[0]
    self.y = position[1]
    
    let size: [Int] = data["size"] as! [Int]
    self.width = size[0]
    self.height = size[1]
}
}
