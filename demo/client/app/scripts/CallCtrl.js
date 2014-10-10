angular.module('phonertcdemo')

  .controller('CallCtrl', function ($scope, $state, $rootScope, $timeout, $stateParams, signaling) {
    var duplicateMessages = [];
    var callStarted = false;

    $scope.callInProgress = false;
    $scope.isCalling = $stateParams.isCalling === 'true';
    $scope.contactName = $stateParams.contactName;
    
    var session;

    function call(isInitiator) {
      var config = { 
        isInitiator: isInitiator,
        turn: {
          host: 'turn:ec2-54-68-238-149.us-west-2.compute.amazonaws.com:3478',
          username: 'test',
          password: 'test'
        },
        streams: {
          audio: true,
          video: true
        }
      };

      session = new cordova.plugins.phonertc.Session(config);
      
      session.on('sendMessage', function (data) { 
        callStarted = true;
        signaling.emit('sendMessage', $scope.contactName, { 
          type: 'phonertc_handshake',
          data: JSON.stringify(data)
        });
      });

      session.on('answer', function () {
        alert('Answered!');
        callStarted = true;
      });

      session.on('disconnect', function () {
        signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
        $state.go('app.contacts');
      });

      session.call();
    }

    if ($scope.isCalling) {
      signaling.emit('sendMessage', $scope.contactName, { type: 'call' });
    }

    $scope.ignore = function () {
      if (callStarted) { 
        session.disconnect();
      } else {
        signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
        $state.go('app.contacts');
      }
    };

    $scope.answer = function () {
      if ($scope.callInProgress) { return; }
      $scope.callInProgress = true;
      $timeout($scope.updateVideoPosition, 1000);

      call(false);

      setTimeout(function () {
        console.log('sending answer');
        signaling.emit('sendMessage', $scope.contactName, { type: 'answer' });
      }, 3500);
    };

    $scope.updateVideoPosition = function () {
      $rootScope.$broadcast('videoView.updatePosition');
    }

    function onMessageReceive (name, message) {
      console.log('messageReceived', name, $scope.contactName, message);

      if (name !== $scope.contactName) {
        return;
      }

      switch (message.type) {
        case 'answer':
          $scope.$apply(function () {
            $scope.callInProgress = true;
            $timeout($scope.updateVideoPosition, 1000);
          });

          call(true);
          break;

        case 'ignore':
          if (callStarted) {
            session.disconnect();
          } else {
            $state.go('app.contacts');
          }

          break;

        case 'phonertc_handshake':
          if (duplicateMessages.indexOf(message.data) === -1) {
            session.receiveMessage(JSON.parse(message.data));
            duplicateMessages.push(message.data);
          } else {
            console.log('-----> prevented duplicate message');
          }
          
          break;
      } 
    }

    signaling.on('messageReceived', onMessageReceive);

    $scope.$on('$destroy', function() { 
      console.log('remove listener');
      signaling.removeListener('messageReceived', onMessageReceive);
    });
  });