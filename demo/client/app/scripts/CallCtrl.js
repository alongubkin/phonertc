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
              host: 'turn:webrtc.dooble.biz:3478',
              username: 'test',
              password: 'test'
          },
          sendMessageCallback: function (data) {
            callStarted = true;
            signaling.emit('sendMessage', $scope.contactName, { 
              type: 'phonertc_handshake',
              data: data
            })
          },
          answerCallback: function () {
            alert('Answered!');
            callStarted = true;
          },
          disconnectCallback: function () {
            signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
            $state.go('app.contacts');
          }
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
      $scope.callInProgress = true;
      call(false);

      setTimeout(function () {
        signaling.emit('sendMessage', $scope.contactName, { type: 'answer' });
      }, 1500);
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
          if (callStarted) {
            cordova.plugins.phonertc.disconnect();
          } else {
            $state.go('app.contacts');
          }

          break;

        case 'phonertc_handshake':
          var dataAsString = JSON.stringify(message.data);

          if (duplicateMessages.indexOf(dataAsString) === -1) {
            cordova.plugins.phonertc.receiveMessage(message.data);
            duplicateMessages.push(dataAsString);
          }
          
          break;
      } 
    });

  });