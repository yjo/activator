/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['services/build', 'text!./run.html', 'css!./run.css', "widgets/navigation/menu"],
    function(build, template, LogView, css){

  var RunState = (function(){
    var self = {};

    self.title = ko.observable("Run");
    self.startStopLabel = ko.computed(function() {
      if (build.run.haveActiveTask())
        return "Stop";
      else
        return "Start";
    }, this);

    self.log = build.run.outputLog;

    // Limit log size
    // TODO: factorise this, maybe add a setting
    self.log.entries.subscribe(function(el) {
      if (el.length > 120){
        self.log.entries.splice(0, el.length - 100);
      }
    });

    // Aliases so we can use these in our html template.
    // This is a mess to clean up; we should just alias
    // 'build' or something then refer to these.
    // But doing this to keep changes in one commit smaller.
    // We want to just change the whole 'build' API anyway.
    self.playAppLink = build.run.playAppLink;
    self.playAppStarted = build.run.playAppStarted;
    self.haveActiveTask = build.activity.running;
    self.haveMainClass = build.run.haveMainClass;
    self.currentMainClass = build.run.currentMainClass;
    self.mainClasses = build.run.mainClasses;
    self.rerunOnBuild = build.settings.rerunOnBuild;
    self.restartPending = build.run.restartPending;
    self.consoleCompatible = build.app.hasConsole;
    self.statusMessage = build.run.statusMessage;

    self.update = function(parameters){
    }
    self.startStopButtonClicked = function(self) {
      debug && console.log("Start or Stop was clicked");
      build.toggleTask('run');
    }
    self.restartButtonClicked = function(self) {
      debug && console.log("Restart was clicked");
      build.restartTask('run');
    }

    return self;
  }());

  return {
    render: function() {
      var $run = $(template)[0];
      ko.applyBindings(RunState, $run);
      return $run;
    },
    route: function(){}
  }
});
