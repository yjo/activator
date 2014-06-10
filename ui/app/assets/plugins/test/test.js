/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "text!./test.html",
  'services/build',
  "css!./test",
  "widgets/navigation/menu"
], function(
  plugins,
  template,
  build
) {

  var TestState = (function(){
    var self = {};

      // aliases to export build model to HTML;
      // these should likely go away someday
      self.results = build.test.results;
      self.testStatus = build.test.testStatus;
      self.hasResults = build.test.hasResults;
      self.haveActiveTask = build.activity.testing;
      self.rerunOnBuild = build.test.rerunOnBuild;
      self.restartPending = build.test.restartPending;
      self.resultStats = build.test.resultStats;

      self.testFilter       = ko.observable('all');
      self.filterTestsText  = ko.computed(function() {
        if(self.testFilter() == 'all')
          return 'Show only failures';
        else
          return 'Show all tests';
      });
      self.displayedResults = ko.computed(function() {
        if(self.testFilter() == 'failures') {
          return ko.utils.arrayFilter(self.results(), function(item) {
            return item.outcome() != build.TestOutcome.PASSED;
          });
        }
        return self.results();
      });
      self.startStopLabel   = ko.computed(function() {
        if (self.haveActiveTask())
          return "Stop";
        else
          return "Start";
      });

      self.filterTests      = function() {
        // TODO - More states.
        if(this.testFilter() == 'all') {
          this.testFilter('failures')
        } else {
          this.testFilter('all')
        }
      }
      self.startStopButtonClicked = function(self) {
        debug && console.log("Start/Stop was clicked");
        build.toggleTask('test');
      }
      self.restartButtonClicked = function(self) {
        debug && console.log("Restart was clicked");
        build.restartTask('test');
      }

      return self;

  }());

  return {
    render: function(url) {
      var $test = $(template)[0];
      ko.applyBindings(TestState, $test);
      return $test;
    },

    route: plugins.memorizeUrl(function(url, breadcrumb) {
      // not used yet
    })
  }

});
