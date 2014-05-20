/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./actors.html', 'commons/utils', '../widget', '../format', '../hover'], function(template, utils, ConsoleWidget, format) {


  return utils.Class(ConsoleWidget, {
    id: 'console-actors-widget',
    template: template,
    init: function(args) {
      var self = this;
      this.columns = [
        { 'name': 'Actor', 'sortBy': 'actor' },
        { 'name': 'Throughput', 'sortBy': 'throughput' },
        { 'name': 'Max Mailbox Time', 'sortBy': 'maxTimeInMailbox' },
        { 'name': 'Max Mailbox Size', 'sortBy': 'maxMailboxSize' },
        { 'name': 'Deviations', 'sortBy': 'deviation' }
      ];
      this.sortBy = ko.observable('actor');
      this.changeSort = function(sortBy) {
        return function(){
          self.sortBy(sortBy);
        }
      };
      this.sortDirection = ko.observable('desc');
      this.data = ko.observable({ 'actors': [], 'total': 0 });
      this.total = ko.computed(function() {
        return self.data().total;
      });
      this.limit = ko.observable('50');
      this.hideAnonymous = ko.observable(false);
      this.fullActorPath = ko.observable(true);
      this.filter = ko.observable('');
      this.clearFilter = function() {
        self.filter('');
      };
      this.actors = ko.computed(function() {
        var data = self.data().actors;
        var fullPath = self.fullActorPath();
        var hideAnonymous = self.hideAnonymous();
        var filter = self.filter();
        var actors = [];
        var formatUnits = function(u, v) { return format.shorten(v) + ' ' + u };
        for (var i = 0; i < data.length; i++) {
          var a = data[i];
          var path = a.scope.actorPath;
          var elements = path.split('/');
          var name = elements.pop();
          var prefix = fullPath ? elements : [];
          var hover = fullPath ? '' : path;
          var actorLink = "#inspect/actor/" + path;
          var messageRate = a.totalMessageRate || 0;
          var throughput = format.units('messages/second', messageRate, formatUnits);
          var maxTimeInMailbox = format.units(a.maxTimeInMailbox.unit, a.maxTimeInMailbox.value, formatUnits);
          var maxMailboxSize = a.maxMailboxSize;
          var deviationCount = a.errorCount + a.warningCount + a.deadletterCount + a.unhandledMessageCount;
          var deviations = deviationCount > 0 ? deviationCount : "";
          if (!self.fullActorPath() && path.indexOf('/user/') > -1) path = path.substring(path.indexOf('/user/') + 6);
          var actor = {
            'path': path,
            'actorLink': actorLink,
            'prefix': prefix,
            'name': name,
            'hover': hover,
            'throughput': throughput,
            'maxTimeInMailbox': maxTimeInMailbox,
            'maxMailboxSize': maxMailboxSize,
            'deviations': deviations
          };
          var show = true;
          if (self.hideAnonymous() && name.charAt(0) == '$') show = false;
          if (filter.length > 0) {
            var filters = filter.toLowerCase().split(' ');
            var matchWith = fullPath ? path.toLowerCase() : name.toLowerCase();
            for (var j = 0; j < filters.length; j++) {
              if (matchWith.indexOf(filters[j]) < 0) show = false;
            }
          }
          if (show) actors.push(actor);
        }
        return actors;
      });
      this.isSystemActor = function(path) {
        if (path.indexOf("/user") == path.length - 5) return true;
        return false;
      };

      this.hasActors = utils.arrayGTZero(self.actors);
    },
    dataName: 'actors',
    dataTypes: ['actors'],
    dataRequest: function() {
      sortBy = this.sortBy();
      if (sortBy == "actor") {
        sortBy = this.fullActorPath() ? "actorPath" : "actorName";
      }
      return {
        'sortCommand': sortBy,
        'sortDirection': this.sortDirection(),
        'paging': { 'offset': 0, 'limit': parseInt(this.limit(), 10) }
      };
    },
    onData: function(data) {
      this.data(data.actors);
    }
  });
});
