angular.module('phonertcdemo')

  .controller('ContactsCtrl', function ($scope, ContactsService) {
    $scope.contacts = ContactsService.onlineUsers;
  });