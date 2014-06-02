/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./overview.html', 'commons/utils', './widget', './format', 'services/connection'], function(template, utils, ConsoleWidget, format, Connection) {


  return utils.Class(ConsoleWidget, {
    id: 'console-overview-widget',
    template: template,
    init: function(args) {
      var self = this;
      this.actors = ko.observable(0);
      this.requests = ko.observable(0);
      this.deviations = ko.observable(0);
      this.currentStorageTime = ko.observable(0);
      this.cacheTime = ko.computed(function() {
        return format.humanReadableDuration(self.currentStorageTime(), "milliseconds");
      });
      this.hasPlayRequests = ko.computed(function() {
        return self.requests() > 0;
      });
      this.hasActors = ko.computed(function() {
        return self.actors() > 0;
      });
      this.hasDeviations = ko.computed(function() {
        return self.deviations() > 0;
      });
    },
    reset: function() {
      Connection.reset()
    },
    active: ko.observable(''),
    dataName: 'overview',
    dataTypes: ['overview'],
    onData: function(data) {
      this.actors(data.metadata.actorPathCount);
      this.requests(data.metadata.playPatternCount);
      this.deviations(data.deviations.deviationCount);
      this.currentStorageTime(data.currentStorageTime);
    },
    dataRequest: function() {
      return {
        'paging': { 'offset': 0, 'limit': 10000 }
      };
    },
    shorten: function(count) {
      return format.shortenNumber(count);
    }
  });
});
