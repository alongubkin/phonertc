angular.module('phonertcdemo')

  .controller('CallCtrl', function ($scope, $state, $stateParams, signaling) {
    var duplicateMessages = [];
    var callStarted = false;

    $scope.callInProgress = false;
    $scope.isCalling = $stateParams.isCalling === 'true';
    $scope.contactName = $stateParams.contactName;
    
    function call(isInitiator) {
      cordova.plugins.phonertc.call({ 
          isInitator: isInitiator, // Caller or callee?
          turn: {
              host: 'turn:ec2-54-68-238-149.us-west-2.compute.amazonaws.com:3478',
              username: 'test',
              password: 'test'
          },
          sendMessageCallback: function (data) {
            callStarted = true;
            signaling.emit('sendMessage', $scope.contactName, { 
              type: 'phonertc_handshake',
              data: JSON.stringify(data)
            })
          },
          answerCallback: function () {
            alert('Answered!');
            callStarted = true;
          },
          disconnectCallback: function () {
            signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
            $state.go('app.contacts');
          },
          /*video: {
            localVideo: document.getElementById('localVideo'),
            remoteVideo: document.getElementById('remoteVideo')
          }*/
        });
    }

    if ($scope.isCalling) {
      signaling.emit('sendMessage', $scope.contactName, { type: 'call' });
    }

    $scope.ignore = function () {
      if (callStarted) { 
        cordova.plugins.phonertc.disconnect();
      } else {
        signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
        $state.go('app.contacts');
      }
    };

    $scope.answer = function () {
      if ($scope.callInProgress) { return; }
      $scope.callInProgress = true;
      
      call(false);

      setTimeout(function () {
        console.log('sending answer');
        signaling.emit('sendMessage', $scope.contactName, { type: 'answer' });
      }, 3500);
    };

    function onMessageReceive (name, message) {
      console.log('messageReceived', name, $scope.contactName, message);

      if (name !== $scope.contactName) {
        return;
      }

      switch (message.type) {
        case 'answer':
          $scope.$apply(function () {
            $scope.callInProgress = true;
          });

          call(true);
          break;

        case 'ignore':
          if (callStarted) {
            cordova.plugins.phonertc.disconnect();
          } else {
            $state.go('app.contacts');
          }

          break;

        case 'phonertc_handshake':
          if (duplicateMessages.indexOf(message.data) === -1) {
            cordova.plugins.phonertc.receiveMessage(JSON.parse(message.data));
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