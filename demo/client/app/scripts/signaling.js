angular.module('phonertcdemo')
  .factory('signaling', function (socketFactory) {
    var socket = io.connect('http://localhost:3000/');
    
    var socketFactory = socketFactory({
      ioSocket: socket
    });

    return socketFactory;
  });