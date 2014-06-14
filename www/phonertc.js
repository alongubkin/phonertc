var exec = require('cordova/exec');

var videoElements;

document.addEventListener("touchmove", function () {
  // This function should listen for scrolling and update the position of the elements to cordova exec
  if (videoElements) {
    // Update Video Element positioning
  }
});

exports.call = function (options) {
  // options should contain a video option if video is enabled
  // sets the initial video options a dom listener needs to be added to watch for movements.
  var video;
  if (options.video) {
    videoElements = video = {};
    videoElements.localVideo = options.video.localVideo;
    videoElements.remoteVideo = options.video.remoteVideo;
    video.localVideo = {
      // get these values by doing a lookup on the dom
      x : videoElements.localVideo.offsetLeft,
      y : videoElements.localVideo.offsetTop,
      width : videoElements.localVideo.offsetWidth,
      height: videoElements.localVideo.offsetHeight
    },
    video.remoteVideo = {
      // get these values by doing a lookup on the dom
      x : videoElements.remoteVideo.offsetLeft,
      y : videoElements.remoteVideo.offsetTop,
      width : videoElements.remoteVideo.offsetWidth,
      height : videoElements.remoteVideo.offsetHeight
    }
  }

  exec(
    function (data) {
      if (data.type === '__answered' && options.answerCallback) {
        options.answerCallback();
      } else if (data.type === '__disconnected' && options.disconnectCallback) {
        options.disconnectCallback();
      } else {
        options.sendMessageCallback(data);
      }
    },
    null,
    'PhoneRTCPlugin',
    'call',
    [options.isInitator, options.turn.host, options.turn.username, options.turn.password, videoElements]);
};

exports.receiveMessage = function (data) {
  exec(
    null,
    null,
    'PhoneRTCPlugin',
    'receiveMessage',
    [JSON.stringify(data)]);
};

exports.disconnect = function () {
  exec(
    null,
    null,
    'PhoneRTCPlugin',
    'disconnect',
    []);
};
