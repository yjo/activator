/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./compile.html', 'main/plugins', 'main/pluginapi', 'services/build', 'commons/settings', 'css!./compile.css', "widgets/navigation/menu"],
  function(template, plugins, api, build, settings, LogView) {

    var CompileState = (function(){
      var self = {};

      self.title = ko.observable("Compile");
      self.startStopLabel = ko.computed(function() {
        if (build.compile.haveActiveTask())
          return "Stop compiling";
        else
          return "Start compiling";
      });
      self.log = build.log;

      // Limit log size
      // TODO: factorise this, maybe add a setting
      self.log.entries.subscribe(function(el) {
        if (el.length > 120){
          self.log.entries.splice(0, el.length - 100);
        }
      });

      // TODO get rid of per-plugin status
      self.status = ko.observable(api.STATUS_DEFAULT);
      // aliased here so our html template can find it
      self.recompileOnChange = build.settings.recompileOnChange;
      self.update = function(parameters){
      };
      self.startStopButtonClicked = function(self) {
        debug && console.log("Start/stop compile was clicked");
        build.toggleTask('compile');
      };

      return self;
    }());

    return {
      render: function() {
        var $compile = $(template)[0];
        ko.applyBindings(CompileState, $compile);
        return $compile;
      },
      route: function(url, breadcrumb) {
      }
    };
  });
