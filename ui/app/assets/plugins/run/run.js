/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['services/build', 'services/newrelic', 'services/appdynamics', 'text!./run.html', 'css!./run.css', "widgets/navigation/menu"],
    function(build, newrelic, appdynamics, template, LogView, css){

  var RunState = (function(){
    var self = {};
    self.monitoringOptions = ko.computed(function() {
      var result = [{ name: "Inspect", id: "inspect", enabled: true}];
      if (newrelic.hasPlay() && newrelic.available() && newrelic.licenseKeySaved()) {
        var enabled = false;
        if (newrelic.isProjectEnabled() == true) {
          enabled = true;
        }
        result.push({ name: "New Relic", id: "newRelic", enabled: enabled, enable: function() {
            newrelic.enableProject(newrelic.licenseKey(), build.app.name());
            self.currentMonitoringOption("newRelic");
          }
        });
      }
      if (appdynamics.available()) {
        result.push({ name: "AppDynamics", id: "appDynamics", enabled: false, enable: function() {
            self.currentMonitoringOption("appDynamics");
          }
        });
      }
      return result;
    }, self);
    self.currentMonitoringOption = ko.observable("inspect");
    self.currentMonitoringOption.subscribe(function (newOption) {
      console.log("run instrumentation changed to: "+newOption);
      build.run.instrumentation(newOption);
      build.restartTask('run');
    });
    self.showMonitoringOptions = ko.computed(function () {
      var options = self.monitoringOptions();
      return (options != undefined && options.length > 1);
    }, self);
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
    self.haveActiveTask = build.run.haveActiveTask;
    self.haveMainClass = build.run.haveMainClass;
    self.currentMainClass = build.run.currentMainClass;
    self.mainClasses = build.run.mainClasses;
    self.rerunOnBuild = build.settings.rerunOnBuild;
    self.restartPending = build.run.restartPending;
    self.consoleCompatible = build.app.hasConsole;
    self.statusMessage = build.run.statusMessage;

    self.update = function(parameters){
    };
    self.startStopButtonClicked = function(self) {
      debug && console.log("Start or Stop was clicked");
      build.toggleTask('run');
    };
    self.restartButtonClicked = function(self) {
      debug && console.log("Restart was clicked");
      build.restartTask('run');
    };

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
