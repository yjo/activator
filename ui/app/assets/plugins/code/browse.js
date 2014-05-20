/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./browse.html', 'commons/utils', 'commons/widget', './files'], function(template, utils, Widget, files) {

  // TODO - Don't duplicate this in both view.js + browse.js...
  function open(location) {
    return $.ajax({
      url: '/api/local/open',
      type: 'GET',
      data: {
        location: location
      }
    });
  }

  function create(location, isDirectory) {
    return $.ajax({
      url: '/api/local/create',
      type: 'PUT',
      dataType: 'text',
      data: {
        location: location,
        isDirectory: isDirectory
      }
    });
  }

  var Browser = utils.Class(Widget, {
    id: 'code-browser-view',
    template: template,
    init: function(config) {
      var self = this;
      self.openInEclipse = config.openInEclipse;
      self.openInIdea = config.openInIdea;
      self.directory = config.directory;
      self.pageType = ko.computed(function(o) {
        return "browser"
      });
      self.files = ko.computed(function() {
        var dir = self.directory();
        return dir.children();
      });
      self.rootAppPath = config.rootAppPath || config.directory().location;
      self.name = ko.computed(function() {
        // TODO - Trim the name in a nicer way
        return './' + self.directory().name();
      });
      self.isEmpty = ko.computed(function() {
        return self.files().length == 0;
      });
      self.parts = ko.computed(function() {
        var parts = self.directory().relative().split('/');
        return $.map(parts, function(name, idx) {
          return {
            name: name,
            url: '#code/' + parts.slice(0, idx+1).join('/')
          };
        });
      });
      self.prevDirUrl = ko.computed(function() {
        var parts = self.directory().relative().split('/');
        return '#code/' + parts.slice(0, parts.length -1).join('/');
      });
    },
    openInFileBrowser: function() {
      var self = this;
      var loc = self.directory().location;
      open(loc).success(function() {}).error(function(err) {
        debug && console.log('Failed to open directory in browser: ', err)
        alert('Failed to open directory.  This may be unsupported by your system.');
      });
    },
    openProjectInFileBrowser: function() {
      var self = this;
      var loc = self.rootAppPath;
      open(loc).success(function() {}).error(function(err) {
        debug && console.log('Failed to open directory in browser: ', err)
        alert('Failed to open directory.  This may be unsupported by your system.');
      });
    },
    newSomething: function(isDirectory) {
      var self = this;
      var message;
      if (isDirectory)
        message = 'Name of folder to create:';
      else
        message = 'Name of file to create:';
      var name = window.prompt(message);
      if (typeof(name) == 'string' && name.length > 0) {
        var full = this.directory().location + "/" + name;
        debug && console.log('Creating file or folder: ', full);
        create(full, isDirectory).done(function () {
          debug && console.log('Success creating file or folder');
          // reload (since we don't watch for changes...)
          self.directory().loadInfo();

          // open file in browser
          if (!isDirectory) {
            var fm = new files.FileModel({
              location: full
            });
            fm.select();
          }
        }).fail(function(err) {
          debug && console.log('Failed to create: ', err);
          alert(err.responseText);
        });
      } else {
        debug && console.log('No name entered, got: ', name)
      }
    },
    newFile: function() {
      this.newSomething(false /* isDirectory */);
    },
    newFolder: function() {
      this.newSomething(true /* isDirectory */);
    }
  });
  return Browser;

});
