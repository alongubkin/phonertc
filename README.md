this is a fork of the project [https://github.com/alongubkin/phonertc](https://github.com/alongubkin/phonertc).
I've just added a package.json to be compliant with cordova 7, and replaced the \*.swift files to be compliant with Swift 3, according to suggestions proposed by the user [@wolfmanwoking](https://github.com/alongubkin/phonertc/issues/235)

#### Problems
 * probably, when compiling, you could have the SJProgressHUD object not recognized. Check if the file it's present in the folder of the installed plugin, otherwise manually add it. You can find this file on this repo, under `/src/ios/SJProgressHUD.swift`
  * if ionic cordova build ios gives other errors, be sure to have updated your build ios version to be compiled with Swift 3. You can do it by `cordova platform update ios`
  * fix the headers in xcode:
    1) Go platforms/ios and click on [ProjectName].xcodeproj to open it with XCode
    2) Go to your project settings
    3) In `General`, change `Deployment Target` to 7.0 or above
    4) Go to `Build Settings` and change:
      `Objective-C Bridging Header` => 
           [ProjectName]/Plugins/com.dooble.phonertc/Bridging-Header.h
      `Runpath Search Paths` => 
           $(inherited) @executable_path/Frameworks
    5) Go to `Build Phases` and unfold `Link Binary With Libraries`
      `Add libicucore.dylib`

### PhoneRTC

<img src="http://phonertc.io/images/logo_black.png" width="400">

WebRTC for Cordova apps!

**Important Note**: Use GitHub issues only for bugs and feature requests! For any other questions, use the [StackOverflow forum](http://stackoverflow.com/questions/tagged/phonertc).

### Features

* Completely Open Source
* Android, iOS and Browser support
* Simple JavaScript API
* Video & Voice calls
* Group calls
* Renegotiation (Mute, Hold, etc) 

Want to learn more? [See the wiki.](https://github.com/alongubkin/phonertc/wiki)

Use the [phonertc](http://stackoverflow.com/questions/tagged/phonertc) tag in StackOverflow for Q/A.

[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=32QXU3V7GM7PC)
