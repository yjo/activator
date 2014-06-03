/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./openInEclipse.html', 'text!./openInIdea.html', 'commons/utils', 'commons/widget', 'services/oldSbt', 'widgets/overlay/overlay', 'services/log', 'widgets/log/log'],
function(eclipseTemplate, ideaTemplate, utils, Widget, sbt, Overlay, Log, LogView){

  function browse(location) {
    return $.ajax({
      url: '/api/local/browse',
      type: 'GET',
      dataType: 'json',
      data: {
        location: location
    }
    });
  }

  var OpenIn = utils.Class(Widget, {
    init: function(parameters) {
      var self = this;
      self.overlay = new Overlay({
        contents: this,
        css: this.overlayClass
      });
      self.log = new Log();
      self.logView = new LogView(self.log);
      self.haveProjectFiles = ko.observable(false);
      self.workingStatus = ko.observable("");
      self.projectDirectory = ko.observable(serverAppModel.location);
      self.activeTask = ko.observable(""); // empty string or taskId
    },
    onRender: function(childElements) {
      var self = this;

      var node = $(childElements[0]).parent();

      self.startNode = node.find('.start');
      self.generateNode = node.find('.generate');
      self.instructionsNode = node.find('.instructions');

      // Start the tutorial
      self._switchTo(null); // show nothing until start() does
      self.start();
    },
    open: function() {
      this.overlay.open();
    },
    close: function() {
      this.overlay.close();
    },
    _updateHaveProjectFiles: function() {
      var self = this;
      browse(serverAppModel.location + "/" + self.projectFilename).done(function(data) {
        self.haveProjectFiles(true);
      }).error(function() {
        self.haveProjectFiles(false);
      });
    },
    // node may be null to hide all pages
    _switchTo: function(node) {
      var self = this;
      var nodes = [self.startNode, self.generateNode, self.instructionsNode];
      $.each(nodes, function(i, value) {
        if (value === node) {
          value.fadeIn();
        } else {
          value.hide();
        }
      });
    },
    start: function() {
      this._updateHaveProjectFiles();
      this._switchTo(this.startNode);
    },
    generate: function() {
      var self = this;
      this._switchTo(this.generateNode);
      if (self.activeTask() == "") {
        self.workingStatus("Generating " + self.ideName + " project files...");
        self.log.clear();
        var taskId = sbt.runTask({
          task: self.taskName,
          onmessage: function(event) {
            debug && console.log("event while generating " + self.ideName + " files ", event);
            self.log.event(event);
          },
          success: function(data) {
            debug && console.log(self.ideName + " result", data);
            self.workingStatus("Successfully created " + self.ideName + " project files.");
            self._updateHaveProjectFiles();
            self.activeTask("");
          },
          failure: function(status, message) {
            debug && console.log(self.ideName + " fail", message);
            self.workingStatus("Failed to generate " + self.ideName + " project files.");
            self._updateHaveProjectFiles();
            self.activeTask("");
          }
        });
        self.activeTask(taskId);
      }
    },
    instructions: function() {
      this._switchTo(this.instructionsNode);
    }
  });

  var OpenInEclipse = utils.Class(OpenIn, {
    id: 'open-in-eclipse-widget',
    template: eclipseTemplate,
    overlayClass: 'open-in-eclipse',
    projectFilename: '.project',
    taskName: 'eclipse',
    ideName: 'Eclipse'
  });

  var OpenInIdea = utils.Class(OpenIn, {
    id: 'open-in-idea-widget',
    template: ideaTemplate,
    overlayClass: 'open-in-idea',
    projectFilename: '.idea',
    taskName: 'gen-idea',
    ideName: 'IntelliJ IDEA'
  });

  return {
    OpenInEclipse : OpenInEclipse,
    OpenInIdea : OpenInIdea
  };
});
