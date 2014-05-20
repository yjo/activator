define(['text!./actor.html', 'commons/utils', './../widget', '../format'], function(template, utils, ConsoleWidget, format) {

  return utils.Class(ConsoleWidget, {
    id: 'console-actor-widget',
    template: template,
    init: function(args) {
      var self = this;
      this.parameters = function(params) {
        self.actorPath(params.toString().replace(/,/g, "/").replace(':', ':/'));
      };
      this.actor = ko.observable();
      this.actorPath = ko.observable();
      this.deviationCount = ko.observable(0);
      this.hasDeviations = utils.valueGTZero(this.deviationCount);
      this.deviationsLink = ko.computed(function() {
        return "#inspect/deviations/" + self.actorPath();
      });
    },
    dataName: 'actor',
    dataTypes: ['actor'],
    dataScope: {},
    dataRequest: function() {
      return { 'scope': { 'actorPath': this.actorPath() } };
    },
    onData: function(data) {
      this.deviationCount(data.actor.errorCount +
        data.actor.warningCount +
        data.actor.deadletterCount +
        data.actor.unhandledMessageCount);
      this.actor(data.actor);
    },
    shorten: function(value) {
      return format.shorten(value, 2);
    },
    formatUnits: function(unit, value) {
      return format.units(unit, value);
    },
    formatDate: function(value) {
      return format.formatTime(new Date(value));
    }
  });
});
