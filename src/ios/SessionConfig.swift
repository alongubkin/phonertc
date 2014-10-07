import Foundation

struct SessionConfig {
    var isInitiator: Bool
    var turn: TurnConfig
}

struct TurnConfig {
    var host: String
    var username: String
    var password: String
}