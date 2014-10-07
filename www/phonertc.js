var exec = require('cordova/exec');

function Session(config) { 
  // make sure that the config object is valid
  if (typeof config !== 'object') {
    throw {
      name: 'PhoneRTC Error',
      message: 'The first argument must be an object.'
    };
  }

  if (typeof config.isInitiator === 'undefined' ||
      typeof config.turn === 'undefined' ||
      typeof config.streams === 'undefined') {
    throw {
      name: 'PhoneRTC Error',
      message: 'isInitiator, turn and streams are required parameters.'
    };
  }

  var self = this;
  self.events = {};
  self.config = config;

  // make all config properties accessible from this object
  Object.keys(config).forEach(function (prop) {
    Object.defineProperty(self, prop, {
      get: function () { return self.config[prop]; },
      set: function (value) { self.config[prop] = value; }
    });
  });

  function callEvent(eventName) {
    if (!self.events[eventName]) {
      return;
    }

    var args = Array.prototype.slice.call(arguments, 1);
    self.events[eventName].forEach(function (callback) {
      callback.apply(self, args);
    });
  }

  function onSendMessage(data) {
    if (data.type === '__answered' && options.answerCallback) {
      callEvent('answer');
    } else if (data.type === '__disconnected' && options.disconnectCallback) {
      callEvent('disconnect');
    } else {
      callEvent('sendMessage', data);
    }
  }

  exec(onSendMessage, null, 'PhoneRTCPlugin', 'createSessionObject', [config]);
};

Session.prototype.on = function (eventName, fn) {
  // make sure that the second argument is a function
  if (typeof fn !== 'function') {
    throw {
      name: 'PhoneRTC Error',
      message: 'The second argument must be a function.'
    };
  }

  // create the event if it doesn't exist
  if (!this.events[eventName]) {
    this.events[eventName] = [];
  } else {
    // make sure that this callback doesn't exist already
    for (var i = 0, len = this.events[eventName].length; i < len; i++) {
      if (this.events[eventName][i] === fn) {
        throw {
          name: 'PhoneRTC Error',
          message: 'This callback function was already added.'
        };
      }
    }
  }

  // add the event
  this.events[eventName].push(fn);
};

Session.prototype.off = function (eventName, fn) {
  // make sure that the second argument is a function
  if (typeof fn !== 'function') {
    throw {
      name: 'PhoneRTC Error',
      message: 'The second argument must be a function.'
    };
  }

  if (!this.events[eventName]) {
    return;
  }

  var indexesToRemove = [];
  for (var i = 0, len = this.events[eventName].length; i < len; i++) {
    if (this.events[eventName][i] === fn) {
      indexesToRemove.push(i);
    }
  }

  indexesToRemove.forEach(function (index) {
    this.events.splice(index, 1);
  })
};

Session.prototype.call = function () {
  exec(null, null, 'PhoneRTCPlugin', 'call', []);
};

Session.prototype.receiveMessage = function (data) {
  exec(null, null, 'PhoneRTCPlugin', 'receiveMessage', [JSON.stringify(data)]);
};

Session.prototype.close = function () {
  exec(null, null, 'PhoneRTCPlugin', 'disconnect', []);
};

exports.Session = Session;