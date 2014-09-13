angular.module('phonertcdemo')

  .controller('CallCtrl', function ($scope, $state, $stateParams, signaling) {
    $scope.callInProgress = false;
    $scope.isCalling = $stateParams.isCalling === 'true';
    $scope.contactName = $stateParams.contactName;
    
    function call(isInitiator) {
      cordova.plugins.phonertc.call({ 
          isInitator: isInitiator, // Caller or callee?
          turn: {
              host: 'turn:webrtc.dooble.biz:3478',
              username: 'test',
              password: 'test'
          },
          sendMessageCallback: function (data) {
            signaling.emit('sendMessage', $scope.contactName, { 
              type: 'phonertc_handshake',
              data: data
            })
          },
          answerCallback: function () {
            alert('Answered!');
          },
          disconnectCallback: function () {
            alert('Call disconnected!');
          }
        });
    }

    if ($scope.isCalling) {
      signaling.emit('sendMessage', $scope.contactName, { type: 'call' });
    }

    $scope.ignore = function () {
      signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
      $state.go('app.contacts');
    };

    $scope.answer = function () {
      $scope.callInProgress = true;
      call(false);

      signaling.emit('sendMessage', $scope.contactName, { type: 'answer' });
    };

    signaling.on('messageReceived', function (name, message) {
      if (name != $scope.contactName) {
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
          $state.go('app.contacts');
          break;

        case 'phonertc_handshake':
          cordova.plugins.phonertc.receiveMessage(message.data);
          break;
      } 
    });

  });