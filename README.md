## PhoneRTC

PhoneRTC is an open-source standalone video/voice chat solution for PhoneGap based on WebRTC.

### Features

* Completely open source.
* Android and iOS support.
* Simple JavaScript API.
* Video and voice chat.
* Use your own servers without relying on any third-party.
* Perfect for hybrid mobile apps using Angular.

### Requirements

* TURN server ([rfc5766-turn-server](https://code.google.com/p/rfc5766-turn-server/) on Amazon EC2 is a good option here)
* Signaling server ([SignalR](http://signalr.net/) if you are using ASP.NET or [socket.io](http://socket.io/) if you are using Node.js) 

### How does PhoneRTC differ from OpenTok, Weemo, etc?

WebRTC is a peer-to-peer protocol, but it still needs some servers: a signaling server for initializing the call and a proxy server if the peer-to-peer connection fails.

Other solutions, such as OpenTok and Weemo, require you to use their own third-party servers. That means they are much easier to use, but that also means that they are less open, have a subscription model, and you are generally less in control.

PhoneRTC allows you to use your own servers, without relying on anyone. 
