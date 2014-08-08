angular.module('cakeReduxModule')
    .controller('ShowTalkCtrl', ['$scope', '$http', '$routeParams', 'talkList','roomSlotFactory','slotFilterService',
        function($scope, $http, $routeParams,talkList,roomSlotFactory,slotFilterService) {
            $scope.filterSlot = slotFilterService.filterValue;
            $scope.roomsSlots = {};


            var talkRef = $routeParams.talkId;
            $scope.showError = false;

            var updateFromServer = function(data) {
                if ($scope.aTalk) {
                    for (var prop in data) {
                        $scope.aTalk[prop] = data[prop];
                    }
                } else {
                    $scope.aTalk = data;
                }
                var talkSpeakers = $scope.aTalk.speakers;
                _.each(talkSpeakers,function(tspeak) {
                    tspeak.otherTalks = _.filter(talkList.allTalks,function(talk) {
                        if (talk.ref == $scope.aTalk.ref) {
                            return false;
                        }
                        var found = false;
                        if (_.findWhere(talk.speakers,{name:tspeak.name})) {
                            found = true;
                        }
                        return found;
                    });

                });
                if ($scope.aTalk) {
                    document.title = $scope.aTalk.title;
                    roomSlotFactory.roomsSlotsForEvent($scope.aTalk.eventId).then(function(rs) {
                        $scope.roomsSlots = rs;
                    })

                }

            };


            $scope.slotFilterUpdated = function() {
                $scope.roomsSlots.slots = slotFilterService.doFilter($scope.roomsSlots.allSlots);
            };


            $scope.aTalk = _.findWhere(talkList.allTalks,{ref: talkRef});
            if ($scope.aTalk) {
                document.title = $scope.aTalk.title;
            }
            if (!$scope.aTalk || !$scope.aTalk.lastModified) {
                $http({method: "GET", url: "data/atalk?talkId=" + talkRef})
                    .success(updateFromServer);
            }

            $scope.reloadTalk = function() {
                $http({method: "GET", url: "data/atalk?talkId=" + talkRef})
                    .success(updateFromServer);
            }

            $scope.newTagTyped = function() {
                var n = $scope.newTag;
                $scope.aTalk.tags.push(n);
            }



            $scope.removeTag = function(tag) {
                var index = $scope.aTalk.tags.indexOf(tag);
                if (index > -1) {
                    $scope.aTalk.tags.splice(index,1);
                }
            }

            $scope.saveTalk = function() {
                $scope.showError = false;
                var savebtn = $(document).find("#saveButton");
                savebtn.button("loading");
                var t = $scope.aTalk;
                var postData = {
                  ref: t.ref,
                  lastModified: t.lastModified,
                  tags : t.tags,
                  state: t.state
                };
                $http({
                    method: "POST",
                    url: "data/editTalk",
                    data: postData
                }).success(function(data) {
                    savebtn.button("reset");
                    if (data.error) {
                        $scope.errormessage = data.error;
                        $scope.showError = true;
                        return;
                    }
                    $scope.aTalk.lastModified = data.lastModified;
                }).error(function(data, status, headers, config) {
                    savebtn.button("reset");
                });
            };

            $scope.publishTalk = function() {
                $scope.showError = false;
              ;
                var t = $scope.aTalk;
                var postData = {
                    ref: t.ref,
                    lastModified: t.lastModified
                };
                $http({
                    method: "POST",
                    url: "data/publishTalk",
                    data: postData
                }).success(function(data) {

                    if (data.error) {
                        $scope.errormessage = data.error;
                        $scope.showError = true;
                        return;
                    }
                    $scope.aTalk.lastModified = data.lastModified;
                    $scope.aTalk.published = data.published;
                }).error(function(data, status, headers, config) {
                    $scope.errormessage = data.error;
                    $scope.showError = true;
                });
            };

            $scope.joinArr = function(arr) {
                if (!arr || arr.length == 0) {
                    return null;
                }
                var res = _.reduce(arr,function(a,b) {
                    return a + ", " + b;
                });
                return res;
            };

            $scope.updateRoom = function() {
                $scope.showError = false;
                var postData = {
                    talkRef: $scope.aTalk.ref,
                    roomRef: $scope.selectedRoom,
                    lastModified: $scope.aTalk.lastModified
                }
                $http({
                    method: "POST",
                    url: "data/assignRoom",
                    data: postData
                }).success(function(data) {
                    if (data.error) {
                        $scope.errormessage = data.error;
                        $scope.showError = true;
                        return;
                    }
                    $scope.aTalk.lastModified = data.lastModified;
                    $scope.aTalk.room = data.room;
                }).error(function(data, status, headers, config) {
                    $scope.errormessage = data.error;
                    $scope.showError = true;
                });
            };

            $scope.updateSlot = function() {
                $scope.showError = false;
                var postData = {
                    talkRef: $scope.aTalk.ref,
                    slotRef: $scope.selectedSlot,
                    lastModified: $scope.aTalk.lastModified
                }
                $http({
                    method: "POST",
                    url: "data/assignSlot",
                    data: postData
                }).success(function(data) {
                    if (data.error) {
                        $scope.errormessage = data.error;
                        $scope.showError = true;
                        return;
                    }
                    $scope.aTalk.lastModified = data.lastModified;
                    $scope.aTalk.slot = data.slot;
                }).error(function(data, status, headers, config) {
                    $scope.errormessage = data.error;
                    $scope.showError = true;
                });
            };



        }]);

