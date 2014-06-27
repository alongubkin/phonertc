var exec = require('cordova/exec');

var videoElements;

document.addEventListener("touchmove", function () {
  // This function should listen for scrolling and update the position of the elements to cordova exec
  if (videoElements) {
    var video = {};
    video.localVideo = {
      x : videoElements.localVideo.getBoundingClientRect().left,
      y : videoElements.localVideo.getBoundingClientRect().top,
      width : videoElements.localVideo.offsetWidth,
      height : videoElements.localVideo.offsetHeight
    };
    video.remoteVideo = {
      x : videoElements.remoteVideo.getBoundingClientRect().left,
      y : videoElements.remoteVideo.getBoundingClientRect().top,
      width : videoElements.remoteVideo.offsetWidth,
      height : videoElements.remoteVideo.offsetHeight
    };
    // Update Video Element positioning
    exec(
      null,
      null,
      'PhoneRTCPlugin',
      'updateVideoPosition',
      [video]);
  }
});

exports.call = function (options) {
  // options should contain a video option if video is enabled
  // sets the initial video options a dom listener needs to be added to watch for movements.
  var video;
  if (options.video) {
    videoElements = {};
    video = {};
    videoElements.localVideo = options.video.localVideo;
    videoElements.remoteVideo = options.video.remoteVideo;
    video.localVideo = {
      // get these values by doing a lookup on the dom
      x : videoElements.localVideo.getBoundingClientRect().left,
      y : videoElements.localVideo.getBoundingClientRect().top,
      width : videoElements.localVideo.offsetWidth,
      height : videoElements.localVideo.offsetHeight
    };
    video.remoteVideo = {
      // get these values by doing a lookup on the dom
      x : videoElements.remoteVideo.getBoundingClientRect().left,
      y : videoElements.remoteVideo.getBoundingClientRect().top,
      width : videoElements.remoteVideo.offsetWidth,
      height : videoElements.remoteVideo.offsetHeight
    };
    videoElements.localVideo.style.visibility = 'hidden';
    videoElements.remoteVideo.style.visibility = 'hidden';
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
    [options.isInitator, options.turn.host, options.turn.username, options.turn.password, video]);
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
