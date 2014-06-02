/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./requests.html', 'commons/utils', './../widget', '../format'], function(template, utils, ConsoleWidget, format) {


  var Requests = utils.Class(ConsoleWidget, {
    id: 'console-requests-widget',
    template: template,
    init: function(args) {
      var self = this;
      this.sortBy = ko.observable('request');
      this.sortDirection = ko.observable('desc');
      this.data = ko.observable({ 'requests': [], 'total': 0 });
      this.limit = ko.observable('25');
      this.requests = ko.observableArray([]);
      this.hasRequests = utils.arrayGTZero(self.requests);
    },
    changeSort: function(sortBy) {
      return function(){
        self.sortBy(sortBy);
      }
    },
    dataName: 'requests',
    dataTypes: ['requests'],
    dataScope: {},
    dataRequest: function() {
      return {
        'sortCommand': this.sortBy(),
        'sortDirection': this.sortDirection(),
        'paging': { 'offset': 0, 'limit': parseInt(this.limit(), 10) }
      };
    },
    onData: function(data) {
      var newRequests = [];
      var requestData = data.playRequestSummaries;
      for (var i = 0; i < requestData.length; i++) {
        var req = requestData[i];
        var requestId = req.traceId.substring(req.traceId.lastIndexOf("/") + 1)
        var requestLink = "#inspect/request/" + requestId;
        var path = req.path;
        var startTime = format.formatTime(req.startTimeMillis);
        var controller = req.controller;
        var controllerMethod = req.controllerMethod;
        var httpMethod = req.httpMethod;
        var invocationTimeMillis = req.invocationTimeMillis;
        var responseCode = req.httpResponseCode;
        var request = {
          'startTime' : startTime,
          'path' : path,
          'requestLink' : requestLink,
          'requestId' : requestId,
          'controller' : controller + "#" + controllerMethod,
          'method' : httpMethod,
          'responseCode' : responseCode,
          'invocationTime' : invocationTimeMillis
        };
        newRequests.push(request);
      }

      this.requests(newRequests);
    }
  });

  return Requests;
});
