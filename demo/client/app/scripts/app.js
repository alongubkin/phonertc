'use strict';

angular.module('phonertcdemo', ['ionic', 
                                'ui.router', 
                                'phonertcdemo.config',
                                'phonertcdemo.registration'])

  .config(function ($stateProvider, $urlRouterProvider) {
    $stateProvider
      .state('app', {
        url: '/app',
        abstract: true,
        templateUrl: 'templates/app.html'
      })
      .state('app.registration', {
        url: '/registration',
        abstract: true,
        template: '<ion-nav-view></ion-nav-view>'
      })
      .state('app.registration.login', {
        url: '/login',
        controller: 'LoginCtrl',
        templateUrl: 'templates/registration/login.html'
      })
      .state('app.registration.register', {
        url: '/register',
        //controller: 'RegisterCtrl',
        templateUrl: 'templates/registration/register.html'
      })
      .state('app.contacts', {
        url: '/contacts',
        templateUrl: 'templates/contacts.html'
      });

    $urlRouterProvider.otherwise('/app/registration/login');
  })

  .run(function($ionicPlatform) {
    $ionicPlatform.ready(function() {
      // Hide the accessory bar by default (remove this to show the accessory bar above the keyboard
      // for form inputs)
      if (window.cordova && window.cordova.plugins.Keyboard) {
        cordova.plugins.Keyboard.hideKeyboardAccessoryBar(true);
      }

      if(window.StatusBar) {
        // org.apache.cordova.statusbar required
        StatusBar.styleDefault();
      }
    });
  });
