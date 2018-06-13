'use strict';

angular.module('cosyan').directive('entityList', ['$http', function($http) {
  return {
    restrict: 'E',
    scope: {
      entities: '=',
      open: '&?',
      delete: '&?',
      pick: '&?',
    },
    templateUrl: 'entity/entity-list.html',
    link: function(scope, element) {
    },
  };
}]);