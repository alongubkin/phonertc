angular.module('phonertcdemo')

  .controller('CallCtrl', function ($scope, $state, $stateParams, signaling) {
    $scope.callInProgress = false;
    $scope.isCalling = $stateParams.isCalling === 'true';
    $scope.contactName = $stateParams.contactName;

    if ($scope.isCalling) {
      signaling.emit('sendMessage', $scope.contactName, { type: 'call' });
    }

    $scope.ignore = function () {
      signaling.emit('sendMessage', $scope.contactName, { type: 'ignore' });
      $state.go('app.contacts');
    };

    $scope.answer = function () {
      $scope.callInProgress = true;
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
          })
          break;

        case 'ignore':
          $state.go('app.contacts');
          break;
      }
    });

  });