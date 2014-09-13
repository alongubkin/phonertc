angular.module('phonertcdemo')

  .controller('LoginCtrl', function ($scope, $state, $ionicPopup, signaling) {
    $scope.loading = false;
    
    $scope.login = function (name) {
      $scope.loading = true;
      signaling.emit('login', name);
    };

    signaling.on('login_error', function (message) {
      $scope.loading = false;
      var alertPopup = $ionicPopup.alert({
        title: 'Error',
        template: message
      });
    });

    signaling.on('login_successful', function (message) {
      $state.go('app.contacts');
    });
  });