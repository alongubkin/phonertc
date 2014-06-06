## PhoneRTC

WebRTC for PhoneGap apps!

**Note:** PhoneRTC is still at very early stages. Right now, it's more like a proof-of-concept than a production-ready library. 

### Features

* Completely open source.
* Android and iOS support.
* Simple JavaScript API.
* Voice chat.
* Use your own servers without relying on any third-parties.
* Perfect for hybrid mobile apps using Angular.

### Requirements

* TURN server - [rfc5766-turn-server](https://code.google.com/p/rfc5766-turn-server/) on Amazon EC2 is a good option here
* Signaling server - [SignalR](http://signalr.net/) if you are using ASP.NET or [socket.io](http://socket.io/) if you are using Node.js are recommended

### Upcoming features

* Video chat (really soon)
* Group chat
* API documentation
* Volume control

### How does PhoneRTC differ from OpenTok, Weemo, etc?

WebRTC is a peer-to-peer protocol, but it still needs some servers: a signaling server for initializing the call and a proxy server if the peer-to-peer connection fails.

Other solutions, such as OpenTok and Weemo, require you to use their own third-party servers. That means they are much easier to use, but that also means that they are less open, have a subscription model, and you are generally less in control.

PhoneRTC allows you to use your own servers, without relying on any third-parties.

### Setting up a TURN server

To set up a TURN server, create an Amazon EC2 instance with the latest Ubuntu. Open the following ports in the instance security group:

    TCP 443
    TCP 3478-3479
    TCP 32355-65535
    UDP 3478-3479
    UDP 32355-65535

Open SSH and run:

    sudo apt-get install rfc5766-turn-server
    
Next, edit `/etc/turnserver.conf` and change the following options:

    listening-ip=<private EC2 ip address>
    relay-ip=<private EC2 ip address>
    external-ip=<public EC2 ip address>
    min-port=32355 
    max-port=65535
    realm=<your domain>
    
Also uncomment the following options:

    lt-cred-mech
    fingerprint 

Next, open `/etc/turnuserdb.conf` and add a new user at the end of the file. The format is: 

    username:password

To start the TURN server, run the following command:

    sudo /etc/init.d/rfc5766-turn-server start

### Plugin Installation

Install Cordova:

    npm install -g cordova ios-deploy
    
Create a new Cordova project:

    cordova create <name>
    cordova platform add ios android

Add the plugin:

    cordova plugin add https://github.com/alongubkin/phonertc.git
    
### Usage Example 
```javascript
phonertc.call({ 
    isInitator: true, // Caller or callee?
    turn: {
        host: 'turn:turn.example.com:3478',
        username: 'user',
        password: 'pass'
    },
    sendMessageCallback: function (data) {
        // PhoneRTC wants to send a message to your target, use
        // your signaling server here to send the message.
        signaling.sendMessage(target, { 
            type: 'webrtc_handshake',
            data: data
        });
    },
    answerCallback: function () {
        alert('Callee answered!');
    },
    disconnectCallback: function () {
        alert('Call disconnected!');
    }
});

signaling.onMessage = function (message) {
    if (message.type === 'webrtc_handshake') {
        // when a message is received from the signaling server, 
        // notify the PhoneRTC plugin.
        phonertc.receiveMessage(message.data);
    }
};
```

### Building

Building for Android is easy. You can just:

    cordova build android
    cordova run android

In iOS, it's slightly more complicated. Run:

    cordova build ios
    
This command will result in an error. Open the project in Xcode and change the following options in the project settings:

    Valid Architectures => armv7
    Build Active Architecture Only => No

In the target choose a real iOS device, not the simulator, otherwise it won't build.

To create an IPA, go to Product > Archive.

### Note on native libraries

The `libs` directory contains compiled libraries from the [official WebRTC project](https://code.google.com/p/webrtc/). If you want to build them yourself, use the following tutorials:

Android: https://code.google.com/p/webrtc/source/browse/trunk/talk/examples/android/README

iOS: https://code.google.com/p/webrtc/source/browse/trunk/talk/app/webrtc/objc/README
