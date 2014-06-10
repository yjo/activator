/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */

define(['lib/knockout/knockout', 'commons/settings', 'services/log', 'commons/utils', 'commons/events', './oldSbt', 'commons/markers', './connection', 'services/sbt'],
    function(ko, settings, Log, utils, events, sbt, markers, Connection, newSbt){

  var rerunOnBuild = settings.observable("build.rerunOnBuild", true);
  var retestOnSuccessfulBuild = settings.observable("build.retestOnSuccessfulBuild", false);
  var automaticResetInspect = settings.observable("build.automaticResetInspect", true);
  var recompileOnChange = settings.observable("build.recompileOnChange", true);

  var Error = utils.Class({
    init: function(fields) {
      this.message = ko.observable(fields.message);
      this.kind = ko.observable(fields.kind);
      this.href = ko.observable(fields.href);
      // owner is a tag used to allow us to remove only
      // errors/warnings from a particular task
      this.owner = fields.owner;
    }
  });
  Error.WARNING = "WARNING";
  Error.ERROR = "ERROR";

  var errorListExtensions = {
    addError: function(owner, message, href) {
      var e = new Error({ kind: Error.ERROR, owner: owner, message: message, href: href })
      this.push(e);
      return e;
    },
    addWarning: function(owner, message, href) {
      var e = new Error({ kind: Error.WARNING, owner: owner, message: message, href: href });
      this.push(e);
      return e;
    },
    clear: function() {
      this.removeAll();
    },
    clearByOwner: function(owner) {
      this.remove(function(item) {
        return item.owner === owner;
      });
    }
  };

  var errorList = ko.observableArray();
  $.extend(errorList, errorListExtensions);

  var filteredErrorList = function(owner) {
    var list = ko.computed(function() {
      var items = errorList();
      var filtered = [];
      for (var i = 0; i < items.length; i++) {
        if (items[i].owner === owner)
          filtered.push(items[i]);
      }
      return filtered;
    });
    return list;
  };

  var errors = {
    all: errorList,
    compile: filteredErrorList("compile"),
    test: filteredErrorList("test"),
    inspect: filteredErrorList("inspect"),
    run: filteredErrorList("run")
  };

  var Status = {
      COMPILING: "COMPILING",
      RUNNING: "RUNNING",
      TESTING: "TESTING",
      INSPECTING: "INSPECTING",
      BUSY: "BUSY",
      IDLE: "IDLE",
      FAILED: "FAILED",
      RESTARTING: "RESTARTING"
  };

  var log = new Log();
  newSbt.legacySubscribeLog(function(event) {
    log.event(event);
  });

  var markerOwner = "build-log";

  // forward errors parsed from the log to errorList
  log.parsedErrorEntries.subscribe(function(changes) {
    // changes[n].index = array index
    // changes[n].status = "added", "deleted"
    // changes[n].value = element value
    $.each(changes, function(i, change) {
      debug && console.log("change", change);
      if (change.status == "added") {
        var entry = change.value;
        if (entry.level == 'warning')
          errorList.addWarning("compile", entry.message, entry.href);
        else
          errorList.addError("compile", entry.message, entry.href);

        // register the error globally so editors can pick it up
        markers.registerFileMarker(markerOwner,
            entry.file, entry.line, entry.level, entry.message);
      } else if (change.status == "deleted") {
        var entry = change.value;
        errorList.remove(function(error) {
          return error.message() == entry.message && error.href() == entry.href;
        });
        // we assume that if any entry is deleted we're going to end
        // up deleting them all, which is lame, but works at the moment
        markers.clearFileMarkers(markerOwner);
      } else {
        debug && console.log("Failed to handle parsed error entries change", change);
      }
    });
  }, null, "arrayChange");

  // properties of the application we are building
  var app = {
    name: ko.observable(window.serverAppModel.name ? window.serverAppModel.name : window.serverAppModel.id),
    hasAkka: ko.observable(false),
    hasPlay: ko.observable(false),
    hasEcho: ko.observable(false)
  };

  var compile = utils.Singleton({
    init: function() {
      var self = this;

      this.status = ko.observable(Status.IDLE);
      this.activeTask = ko.observable(""); // empty string or taskId
      this.haveActiveTask = ko.computed(function() {
        return self.activeTask() != "";
      }, this);
      this.needCompile = ko.observable(false);

      events.subscribe(function(event) {
        return event.type == 'FilesChanged';
      },
      function(event) {
        if (recompileOnChange()) {
          if (app.hasPlay()) {
            debug && console.log("files changed but it's a Play app so not recompiling.")
            log.info("Some of your files may have changed; reload in the browser or click \"Start compiling\" above to recompile.")
            log.info("  (for Play apps, Activator does not auto-recompile because it may conflict with compilation on reload in the browser.)")
          } else {
            debug && console.log("files changed, doing a recompile");
            // doCompile just marks a compile pending if one is already
            // active.
            self.doCompile();
          }
        } else {
          debug && console.log("recompile on change unchecked, doing nothing");
        }
      });

      // we generally expect to get this event on startup, which
      // will then cause us to reload sources, which will then
      // trigger a FilesChanged which will trigger a compile.
      events.subscribe(function(event) {
        debug && console.log("filtering event: ", event);
        return event.type == 'SourcesMayHaveChanged';
      },
      function(event) {
        debug && console.log("Sources may have changed, reloading the list");
        self.reloadSources(null);
      });
    },
    logEvent: function(event) {
      if (log.event(event)) {
        // logged already
      } else {
        log.leftoverEvent(event);
      }
    },
    reloadProjectInfoThenCompile: function() {
      var self = this;

      log.info("Loading details of this project...");

      self.status(Status.COMPILING);
      var taskId = sbt.runTask({
        task: 'name',
        onmessage: function(event) {
          debug && console.log("event from name task ", event);
          self.logEvent(event);
        },
        success: function(result) {
          debug && console.log("name task results ", result);
          self.activeTask("");

          app.name(result.params.name);
          app.hasAkka(result.params.hasAkka === true);
          app.hasPlay(result.params.hasPlay === true);
          app.hasEcho(result.params.hasEcho === true);

          log.debug("name=" + app.name() +
              " hasAkka=" + app.hasAkka() +
              " hasPlay=" + app.hasPlay() +
              " hasEcho=" + app.hasEcho());

          self.compileAfterReloadProjectInfo();
        },
        failure: function(status, message) {
          debug && console.log("loading project info failed", message);
          self.activeTask("");
          log.warn("Failed to load project details: " + message);
          self.status(Status.FAILED);
        }
      });
      self.activeTask(taskId);
    },
    // after = optional
    reloadSources: function(after) {
      var self = this;

      log.info("Refreshing list of source files to watch for changes...");
      self.status(Status.BUSY);
      sbt.watchSources({
        onmessage: function(event) {
          debug && console.log("event watching sources", event);
          self.logEvent(event);
        },
        success: function(data) {
          debug && console.log("watching sources result", data);
          self.status(Status.IDLE);
          log.info("Will watch " + data.count + " source files.");
          if (typeof(after) === 'function')
            after();
        },
        failure: function(status, message) {
          debug && console.log("watching sources failed", message);
          log.warn("Failed to reload source file list: " + message);
          self.status(Status.FAILED);
          if (typeof(after) === 'function')
            after();
        }
      });
    },
    afterCompile: function(succeeded) {
      var self = this;

      if (succeeded)
        self.status(Status.IDLE);
      else
        self.status(Status.FAILED);

      if (self.needCompile()) {
        debug && console.log("need to recompile because something changed while we were compiling");
        self.needCompile(false);
        self.doCompile();
      } else if (succeeded) {
        // asynchronously reload the list of sources in case
        // they changed. we are trying to serialize sbt usage
        // here so we only send our event out when we finish
        // with the reload.
        self.reloadSources(function() {
          // notify others
          events.send({ 'type' : 'CompileSucceeded' });
        });
      }
    },
    compileAfterReloadProjectInfo: function() {
      var self = this;

      if (self.needCompile()) {
        // asked to restart while loading the project info; so bail out here
        // without starting the actual compile task.
        log.info('Recompile requested.');
        self.afterCompile(false);
        return;
      }

      self.status(Status.COMPILING);
      log.info("Compiling...");
      var task = { task: 'compile' };
      var taskId = sbt.runTask({
        task: task,
        onmessage: function(event) {
          self.logEvent(event);
        },
        success: function(data) {
          debug && console.log("compile result: ", data);
          self.activeTask("");
          if (data.type == 'GenericResponse') {
            log.info('Compile complete.');
          } else {
            log.error('Unexpected reply: ' + JSON.stringify(data));
          }
          self.afterCompile(true); // true=success
        },
        failure: function(status, message) {
          debug && console.log("compile failed: ", status, message)
          self.activeTask("");
          log.error("Request failed: " + status + ": " + message);
          self.afterCompile(false); // false=failed
        }
      });
      self.activeTask(taskId);
    },
    // this does reload project info task, compile task, then watch sources task.
    doCompile: function() {
      var self = this;
      if (self.haveActiveTask()) {
        debug && console.log("Attempt to compile with a compile already active, will recompile again when we finish");
        self.needCompile(true);
        return;
      }

      log.clear();
      self.reloadProjectInfoThenCompile();
    },
    stopCompile: function() {
      var self = this;
      if (self.haveActiveTask()) {
        sbt.killTask({
          taskId: self.activeTask(),
          success: function(data) {
            debug && console.log("kill success: ", data)
          },
          failure: function(status, message) {
            debug && console.log("kill failed: ", status, message)
            log.error("Killing task failed: " + status + ": " + message)
          }
        });
      }
    }
  });

  var run = utils.Singleton({
    init: function() {
      var self = this;

      this.activeTask = ko.observable(""); // empty string or taskId
      this.haveActiveTask = ko.computed(function() {
        return self.activeTask() != "";
      }, this);
      this.mainClasses = ko.observableArray();
      // note: we generally want to use the explicitly-set defaultMainClass even if it
      // isn't in the list of discovered mainClasses
      this.defaultMainClass = ko.observable("");
      this.currentMainClass = ko.observable("");
      this.haveMainClass = ko.computed(function() {
        // when we set mainClasses to empty list (as it is by default), knockout will set
        // currentMainClass to 'undefined'
        return typeof(self.currentMainClass()) == 'string' && self.currentMainClass() != "";
      }, this);
      this.haveActiveTask.subscribe(function(active) {
        if (!active) events.send({ 'type' : 'RunStopped' });
      });
      this.restartPending = ko.observable(false);
      this.reloadMainClassPending = ko.observable(true);
      // last task ID we tried to stop
      this.stoppingTaskId = '';

      events.subscribe(function(event) {
        return event.type == 'CompileSucceeded';
      },
      function(event) {
        self.onCompileSucceeded(event);
      });

      this.playAppLink = ko.observable('');
      this.playAppStarted = ko.computed(function() { return self.haveActiveTask() && self.playAppLink() != ''; }, this);
      this.atmosLink = ko.observable('');
      this.inspecting = ko.computed(function() {
        return this.atmosLink() != '';
      }, this);

      this.status = ko.observable(Status.IDLE);
      this.statusMessage = ko.observable('Application is stopped.');
      this.outputLog = new Log();
    },
    loadMainClasses: function(success, failure) {
      var self = this;

      // the spaghetti here is getting really, really bad.
      function taskCompleteShouldWeAbort() {
        if (!self.haveActiveTask())
          debug && console.log("BUG should not call this without an active task");
        var weWereStopped = (self.activeTask() == self.stoppingTaskId);

        // clear out our task always
        self.activeTask('');

        if (self.restartPending()) {
          debug && console.log("Need to start over due to restart");
          log.debug("Restarting...");
          self.restartPending(false);
          self.loadMainClasses(success, failure);
          // true = abort abort
          return true;
        } else if (weWereStopped) {
          debug && console.log("Stopped, restart not requested");
          log.debug("Stopped");
          // true = abort abort
          return true;
        } else {
          // false = continue
          return false;
        }
      }

      debug && console.log("launching discoveredMainClasses task");
      log.debug("launching discoveredMainClasses task");
      var taskId = sbt.runTask({
        task: 'discovered-main-classes',
        onmessage: function(event) {
          debug && console.log("event discovering main classes", event);
          log.event(event);
        },
        success: function(data) {
          debug && console.log("discovered main classes result", data);

          if (taskCompleteShouldWeAbort())
            return;

          var names = [];
          if (data.type == 'GenericResponse') {
            names = data.params.names;
            log.debug("Discovered main classes: " + names);
          } else {
            log.debug("No main classes discovered");
          }
          log.debug("Got auto-discovered main classes, looking for a default mainClass setting if any");
          function noDefaultMainClassLogging(message) {
            if (names.length > 0) {
              log.debug("Didn't find a default mainClass setting, we'll just pick one of: " + names);
            } else {
              if (message)
                log.error(message);
              log.error("Didn't auto-discover a main class, and no mainClass was set");
            }
          }
          debug && console.log("launching mainClass task");
          log.debug("launching mainClass task");
          var taskId = sbt.runTask({
            task: 'main-class',
            onmessage: function(event) {
              debug && console.log("event getting default main class", event);
              log.event(event);
            },
            success: function(data) {
              debug && console.log("default main class result", data);

              if (taskCompleteShouldWeAbort())
                return;

              var name = '';
              // 'name' won't be in here if mainClass was unset
              if (data.type == 'GenericResponse' && 'name' in data.params) {
                name = data.params.name;
                log.debug("Default main class is '" + name + "'");
              } else {
                // this isn't what really happens if it's not configured, I think
                // sbt just tries to ask the user to pick, which fails, and we
                // get the failure callback. But log just in case.
                noDefaultMainClassLogging();
              }
              success({ name: name, names: names });
            },
            failure: function(status, message) {
              // a common reason for fail is that sbt tried to ask /dev/null to
              // pick a main class manually.
              debug && console.log("getting default main class failed", message);

              if (taskCompleteShouldWeAbort())
                return;

              noDefaultMainClassLogging();
              // we don't treat this as failure, just as no default set
              success({ name: '', names: names });
            }
          });
          self.activeTask(taskId);
        },
        failure: function(status, message) {
          debug && console.log("getting main classes failed", message);

          if (taskCompleteShouldWeAbort())
            return;

          log.debug("Failed to discover main classes: " + message);
          failure(status, message);
        }
      });
      self.activeTask(taskId);
    },
    onCompileSucceeded: function(event) {
      var self = this;

      debug && console.log("Compile succeeded - marking need to reload main class info");
      self.reloadMainClassPending(true);
      if (rerunOnBuild()) {
        debug && console.log("Restarting due to completed compile");
        self.doRestart();
      } else {
        debug && console.log("Run-on-compile not enabled, but we want to load main classes to fill in the option menu.");
        self.doMainClassLoadThenMaybeRun(false /* shouldWeRun */);
      }
    },
    beforeRun: function(shouldWeRun) {
      var self = this;
      if (self.reloadMainClassPending()) {
        log.info("Loading main class information...");
        self.statusMessage('Loading main class...');
        // if we aren't going to run, displaying that we're busy is just confusing.
        if (shouldWeRun)
          self.status(Status.BUSY);
        else
          self.status(Status.IDLE);
      } else {
        self.statusMessage('Running...');
        self.status(Status.RUNNING);
        log.info("Running...");
      }

      self.restartPending(false);
    },
    doRunWithMainClassLoad: function() {
      debug && console.log("doRunWithMainClassLoad")
      this.doMainClassLoadThenMaybeRun(true /* shouldWeRun */);
    },
    doMainClassLoadThenMaybeRun: function(shouldWeRun) {
      var self = this;

      debug && console.log("doMainClassLoadThenMaybeRun, shouldWeRun=" + shouldWeRun)

      self.beforeRun(shouldWeRun);

      // whether we get main classes or not we'll try to
      // run, but get the main classes first so we don't
      // fail if there are multiple main classes.
      function afterLoadMainClasses() {
        self.reloadMainClassPending(false);

        if (shouldWeRun) {
          log.debug("Done loading main classes - now running the project");
          self.doRunWithoutMainClassLoad(true /* clearLogs */);
        } else {
          self.status(Status.IDLE);
        }
      }

      // update our list of main classes
      this.loadMainClasses(function(data) {
        // SUCCESS
        debug && console.log("GOT main class info ", data);

        self.defaultMainClass(data.name);
        debug && console.log("Set default main class to " + self.defaultMainClass());
        // ensure the default configured class is in the menu
        if (self.defaultMainClass() != '' && data.names.indexOf(self.defaultMainClass()) < 0)
          data.names.push(self.defaultMainClass());

        // when we set mainClasses, knockout will immediately also set currentMainClass to one of these
        // due to the data binding on the option menu.
        var actualCurrent = self.currentMainClass();
        if (typeof(actualCurrent) == 'undefined')
          actualCurrent = '';
        var newCurrent = '';

        debug && console.log("Current main class was: '" + actualCurrent + "'");
        // so here's where knockout makes currentMainClass into something crazy
        self.mainClasses(data.names);
        debug && console.log("Set main class options to " + self.mainClasses());

        // only force current selection to change if it's no longer
        // discovered AND no longer explicitly configured in the build.
        if (actualCurrent != '' && self.mainClasses().indexOf(actualCurrent) >= 0) {
          newCurrent = actualCurrent;
          debug && console.log("Keeping current main class since it still exists: '" + newCurrent + "'");
        }

        // if no existing setting, try to set it
        if (newCurrent == '') {
          if (self.defaultMainClass() != '') {
            debug && console.log("Setting current main class to the default " + self.defaultMainClass());
            newCurrent = self.defaultMainClass();
          } else if (self.mainClasses().length > 0) {
            debug && console.log("Setting current main class to the first in our list");
            newCurrent = self.mainClasses()[0];
          } else {
            debug && console.log("We have nothing to set the current main class to");
            newCurrent = '';
          }
        }

        debug && console.log("Current main class is now: '" + newCurrent + "'");
        self.currentMainClass(newCurrent);

        afterLoadMainClasses();
      },
      function(status, message) {
        // FAIL
        debug && console.log("FAILED to set up main classes");
        afterLoadMainClasses();
      });
    },
    doAfterRun: function() {
      var self = this;
      self.activeTask("");
      self.playAppLink("");
      self.atmosLink("");
      if (self.restartPending()) {
        self.doRun();
      }
    },
    doRunWithoutMainClassLoad: function(clearLogs) {
      var self = this;

      debug && console.log("doRunWithoutMainClassLoad")

      self.outputLog.clear();

      if (clearLogs)
        log.clear();

      self.beforeRun(true /* shouldWeRun */);

      // as a special-case, exclude Play's main class since running
      // it "by hand" with run-main does not work. But people can
      // still pick it in the dropdown to mean "play run"
      var mainClassIsPlayServer =
        self.currentMainClass() == 'play.core.server.NettyServer';
      if (mainClassIsPlayServer)
        log.debug("Using 'run' rather than 'run-main' for Play's server class")

      var task = {};
      if (self.haveMainClass() && !mainClassIsPlayServer) {
        task.task = 'run-main';
        task.params = { mainClass: self.currentMainClass() };
      } else {
        task.task = 'run';
      }

      if (build.app.hasEcho()) {
        task.task = 'echo:' + task.task;
      }

      debug && console.log("launching " + task.task + " task");
      var taskId = sbt.runTask({
        task: task,
        onmessage: function(event) {
          if (event.type == 'LogEvent') {
            var logType = event.entry.type;
            if (logType == 'stdout' || logType == 'stderr') {
              self.outputLog.event(event);
            } else {
              log.event(event);
            }
          } else if (event.type == 'Started') {
            // our request went to a fresh sbt, and we witnessed its startup.
            // we may not get this event if an sbt was recycled.
            // we move "output" to "logs" because the output is probably
            // just sbt startup messages that were not redirected.
            log.moveFrom(self.outputLog);
          } else if (event.id == 'playServerStarted') {
            var port = event.params.port;
            var url = 'http://localhost:' + port;
            self.playAppLink(url);
          } else if (event.id == 'atmosStarted') {
            self.atmosLink(event.params.uri);
            events.send({ 'type' : 'AtmosStarted' });
          } else {
            log.leftoverEvent(event);
          }
        },
        success: function(data) {
          debug && console.log("run result: ", data);
          if (data.type == 'GenericResponse') {
            log.info('Run complete.');
            self.statusMessage('Run complete');
            self.status(Status.IDLE);
          } else {
            log.error('Unexpected reply: ' + JSON.stringify(data));
          }
          self.doAfterRun();
        },
        failure: function(status, message) {
          debug && console.log("run failed: ", status, message)
          self.statusMessage('Run failed');
          self.status(Status.FAILED);
          log.error("Failed: " + status + ": " + message);
          self.doAfterRun();
        }
      });
      self.activeTask(taskId);
    },
    doRun: function() {
      if (automaticResetInspect()) {
        Connection.reset();
      }

      if (this.reloadMainClassPending())
        this.doRunWithMainClassLoad();
      else
        this.doRunWithoutMainClassLoad(true /* clearLogs */);
    },
    doStop: function() {
      var self = this;
      if (self.haveActiveTask()) {
        self.stoppingTaskId = self.activeTask();
        sbt.killTask({
          taskId: self.activeTask(),
          success: function(data) {
            debug && console.log("kill success: ", data);
            self.status(Status.IDLE);
          },
          failure: function(status, message) {
            debug && console.log("kill failed: ", status, message)
            self.statusMessage('Unable to stop');
            self.status(Status.IDLE);
            log.error("HTTP request to kill task failed: " + message)
          }
        });
      }
    },
    doRestart: function() {
      if (this.haveActiveTask()) {
        this.doStop();
        this.restartPending(true);
        this.status(Status.RESTARTING);
      } else {
        this.doRun();
      }
    }
  });

  var TestOutcome = {
    PASSED: 'passed',
    FAILED: 'failed',
    ERROR: 'error',
    SKIPPED: 'skipped',
    // PENDING doesn't arrive from server, it's just a state we use locally
    PENDING: 'pending'
  };

  var TestResult = utils.Class({
    init: function(config) {
      var self = this;
      self.errorList = null;
      self.name = config.name;
      self.outcome = ko.observable(config.outcome);
      self.failed = ko.computed(function() {
        return self.outcome() === TestOutcome.FAILED || self.outcome() === TestOutcome.ERROR;
      });
      self.description = ko.observable(config.description);
      self.outcomeClass = ko.computed(function() {
        var current = self.outcome();
        if (current === TestOutcome.PASSED || current === TestOutcome.PENDING) {
          return current;
        } else {
          return TestOutcome.FAILED;
        }
      });

      this.failed.subscribe(function(failed) {
        if (failed)
          self._addError();
        else
          self._removeError();
      });
    },
    // Update our state from an event.
    update: function(event) {
      this.description(event.description);
      this.outcome(event.outcome);
    },
    _removeError: function() {
      var self = this;
      if (this.errorList !== null) {
        this.errorList.remove(function(error) {
          return error.testResult === self;
        });
      }
    },
    _addError: function() {
      this._removeError(); // ensure no dups
      if (this.errorList !== null) {
        var error = this.errorList.addError("test", "Test " + this.name + " failed", "#test");
        error.testResult = this; // to use to remove
      }
    },
    attachToErrorList: function(errorList) {
      this._removeError();
      this.errorList = errorList;
      if (this.failed()) {
        this._addError();
      }
    },
    detachFromErrorList: function(errorList) {
      this._removeError();
      this.errorList = null;
    }
  });

  var test = utils.Singleton({
    init: function() {
      var self = this;

      this.results = ko.observableArray();
      this.testStatus = ko.observable('Waiting to test');
      // TODO - Store state beyond the scope of this widget!
      // We should probably be listening to tests *always*
      // and displaying latest status *always*.
      self.hasResults = ko.computed(function() {
        return self.results().length > 0;
      });
      this.activeTask = ko.observable(""); // empty string or taskId
      this.haveActiveTask = ko.computed(function() {
        return self.activeTask() != "";
      }, this);
      this.rerunOnBuild = retestOnSuccessfulBuild;
      this.restartPending = ko.observable(false);
      this.lastTaskFailed = ko.observable(false);
      // Rollup results.
      this.resultStats = ko.computed(function() {
        var results = {
            passed: 0,
            failed: 0
        };
        $.each(self.results(), function(idx, result) {
          if(result.outcome() != build.TestOutcome.PASSED) {
            results.failed = results.failed + 1;
          } else {
            results.passed = results.passed + 1;
          }
        });
        return results;
      });
      this.status = ko.computed(function() {
        var anyFailures = this.lastTaskFailed() || this.resultStats().failed > 0;

        if (this.haveActiveTask())
          return Status.BUSY;
        else if (anyFailures)
          return Status.FAILED;
        else if (this.restartPending())
          return Status.RESTARTING;
        else
          return Status.IDLE;
      }, this);

      events.subscribe(function(event) {
        return event.type == 'CompileSucceeded';
      },
      function(event) {
        self.onCompileSucceeded(event);
      });

      this.lastTaskFailed.subscribe(function(failed) {
        var message = "Error in test task (check compile logs)";
        if (failed) {
          errorList.addError("test", message, "#compile");
        } else {
          errorList.remove(function(error) {
            return error.owner === "test" && error.message() === message;
          });
        }
      });

      // forward failed results to the error list
      this.results.subscribe(function(changes) {
        // changes[n].index = array index
        // changes[n].status = "added", "deleted"
        // changes[n].value = element value
        $.each(changes, function(i, change) {
          if (change.status == "added") {
            var result = change.value;
            result.attachToErrorList(errorList);
          } else if (change.status == "deleted") {
            var result = change.value;
            result.detachFromErrorList(errorList);
          } else {
            debug && console.log("Failed to handle test results change", change);
          }
        });
      }, null, "arrayChange");
    },
    doAfterTest: function() {
      var self = this;
      self.activeTask("");
      if (self.restartPending()) {
        self.doTest(false); // false=!triggeredByBuild
      }
    },
    doTest: function(triggeredByBuild) {
      var self = this;

      log.clear();
      self.results.removeAll();
      self.testStatus('Running tests...')

      if (triggeredByBuild) {
        log.info("Build succeeded, testing...");
      } else if (self.restartPending()) {
        log.info("Restarting...");
      } else {
        log.info("Testing...");
      }

      self.restartPending(false);

      // TODO - Do we want to clear the test data we had previously
      // or append?  Tests may disappear and we'd never know...

      var taskId = sbt.runTask({
        task: 'test',
        onmessage: function(event) {
          if (log.event(event)) {
            // nothing
          } else if (event.type == 'GenericEvent' &&
              event.task == 'test' &&
              event.id == 'result') {
            self.updateTest(event.params);
          } else if (event.type == 'Started') {
            // this is expected when we start a new sbt, but we don't do anything with it
          } else {
            log.leftoverEvent(event);
          }
        },
        success: function(data) {
          debug && console.log("test result: ", data);

          if (data.type == 'GenericResponse') {
            log.info('Testing complete.');
            self.testStatus('Testing complete.');
          } else {
            log.error('Unexpected reply: ' + JSON.stringify(data));
            self.testStatus("Unexpected: " + JSON.stringify(data));
          }
          self.lastTaskFailed(false);
          self.doAfterTest();
        },
        failure: function(status, message) {
          debug && console.log("test failed: ", status, message)
          log.error("Failed: " + status + ": " + message);
          self.testStatus('Testing error: ' + message);
          self.lastTaskFailed(true);
          self.doAfterTest();
        }
      });
      self.activeTask(taskId);
    },
    updateTest: function(params) {
      var match = ko.utils.arrayFirst(this.results(), function(item) {
        return params.name === item.name;
      });
      if(!match) {
        var test = new TestResult(params);
        this.results.push(test);
      } else {
        if (match.outcome == 'passed') {
          match.update(params);
        }
      }
    },
    onCompileSucceeded: function(event) {
      var self = this;
      if (self.rerunOnBuild() && !self.haveActiveTask()) {
        self.doTest(true); // true=triggeredByBuild
      }
    },
    doStop: function() {
      var self = this;
      if (self.haveActiveTask()) {
        sbt.killTask({
          taskId: self.activeTask(),
          success: function(data) {
            debug && console.log("kill success: ", data)
          },
          failure: function(status, message) {
            debug && console.log("kill failed: ", status, message)
            log.error("HTTP request to kill task failed: " + message)
          }
        });
      }
    },
    doRestart: function() {
      if (this.haveActiveTask()) {
        this.doStop();
        this.restartPending(true);
        this.status(Status.RESTARTING);
      } else {
        this.doTest(false);
      }
    }
  });

  // TODO do this for real like the other ones
  var inspect = {
    status : ko.computed(function() {
      if (run.inspecting())
        return Status.INSPECTING;
      else
        return Status.IDLE;
    })
  };

  var statuses = {
    all: ko.computed(function() {
      // grab all of these always so that ko.computed
      // knows our dependencies
      var haveErrors = errorList().length > 0;
      var runStatus = run.status();
      var compileStatus = compile.status();
      var testStatus = test.status();
      var inspectStatus = inspect.status();

      if (inspectStatus == Status.INSPECTING)
        return Status.INSPECTING;
      else if (runStatus === Status.RUNNING)
        return Status.RUNNING;
      else if (testStatus === Status.TESTING)
        return Status.TESTING;
      else if (compileStatus == Status.COMPILING)
        return Status.COMPILING;
      else if (runStatus === Status.RESTARTING)
        return Status.RESTARTING;
      else if (inspectStatus === Status.BUSY ||
          runStatus === Status.BUSY ||
          testStatus === Status.BUSY ||
          compileStatus == Status.BUSY)
        return Status.BUSY;
      else if (haveErrors)
        return Status.FAILED;
      else
        return Status.IDLE;
    }),
    compile: compile.status,
    test: test.status,
    run: run.status,
    inspect: inspect.status
  };

  var findExecution = function(command) {
    return $.grep(newSbt.executions(), function(item, i) {
      return item.command == command || (item.command.indexOf(':' + command) >= 0);
    });
  };

  var executionExists = function(command) {
    var matching = findExecution(command);
    return matching.length > 0;
  };

  var isTaskActive = function(name) {
    return executionExists(name);
  };

  // a different way to view status with just booleans
  var activity = {
      // TODO ideally "compiling" would mean "an execution is running
      // which has the compile task involved and we aren't up to the compile
      // task yet" or something like that, so that when doing "test" we would
      // also show for a bit that we are doing a compile.
      compiling: ko.computed(function() {
        return isTaskActive('compile');
      }),
      running: ko.computed(function() {
        // TODO see 'launching' below, this needs to be sorted out.
        return isTaskActive('run');
      }),
      testing: ko.computed(function() {
        return isTaskActive('test');
      }),
      inspecting: ko.observable(false) // FIXME
  };
  activity.busy = ko.computed(function() {
    return newSbt.executions().length > 0;
  });
  // this is meant to be "we are working on running but
  // not started up yet" (mutually exclusive with "running")
  activity.launching = ko.computed(function() {
    // TODO update this for newSbt
    var status = run.status();
    if (activity.running())
      return false;
    else {
      debug && console.log("Run status is: " + status);
      return status != Status.IDLE && status != Status.FAILED;
    }
  });

  var startTask = function(name) {
    newSbt.requestExecution(name);
  };

  var restartsPending = {};

  var stopTask = function(name) {
    restartsPending[name] = false;
    var matching = findExecution(name);
    $.each(matching, function(i, item) {
      newSbt.cancelExecution(item.executionId);
    });
  };

  var toggleTask = function(name) {
    if (isTaskActive(name))
      stopTask(name);
    else
      startTask(name);
  };

  var restartTask = function(name) {
    stopTask(name); // sets restartsPending to false
    restartsPending[name] = true;
    // FIXME we need to observe when the task completes
    // and then start it again if a restart is pending
  };

  var stopAllTasks = function() {
    $.each(newSbt.executions(), function(i, item) {
      newSbt.cancelExecution(item.executionId);
    });
  };

  var restartAllTasks = function() {
    $.each(newSbt.executions(), function(i, item) {
      restartTask(item.command);
    });
  };

  var statusTooltips = {
      run: ko.computed(function() {
        if (activity.running())
          return "Application running (click to stop)";
        else if (activity.launching())
          return "Launching application (click to stop)";
        else if (run.status() == Status.FAILED)
          return "Application failed to start or was killed (click to re-run)";
        else if (rerunOnBuild())
          return "Application stopped (will auto-run when build completes)";
        else
          return "Application stopped (click to run it)";
      })
  };

  var onToggleRun = function() {
    debug && console.log("onToggleRun, run active=" + isTaskActive('run'))
    // whenever we manually run, we set to auto-run
    // on build; when we manually stop, we set to
    // not auto run. Not sure whether this will
    // work out nicely but let's try it.
    if (isTaskActive('run')) {
      rerunOnBuild(false);
      stopTask('run');
    } else {
      rerunOnBuild(true);
      startTask('run');
    }
  };

  var build = utils.Singleton({
    init: function() {
    },
    // These APIs are (relatively) reasonable
    Error: Error,
    errors: errors, // errors.{compile,run,etc} = observable array of Error
    Status: Status,
    status: statuses, // status.{compile,run,etc} = observable Status.FOO strings
    statusTooltips: statusTooltips, // statusTooltips.{run} = tooltip describing status
    activity: activity, // convenience booleans when full status detail isn't needed
    log: log,
    app: app,
    TestOutcome: TestOutcome,
    TestResult: TestResult,
    // launch/stop compile, test, run
    // FIXME TODO all of these methods currently affect executions, not tasks.
    // Need to be renamed.
    startTask: startTask,
    stopTask: stopTask,
    toggleTask: toggleTask,
    restartTask: restartTask,
    stopAllTasks: stopAllTasks,
    restartAllTasks: restartAllTasks,
    onToggleRun: onToggleRun,
    // these three are the old clunky APIs, probably
    // best to wrap them with a new nicer API above,
    // rather than use directly. The problem with these
    // is that they are fragile and side-effecty.
    compile: compile,
    run: run,
    test: test,
    settings: {
      rerunOnBuild: rerunOnBuild,
      retestOnSuccessfulBuild: retestOnSuccessfulBuild,
      automaticResetInspect: automaticResetInspect,
      recompileOnChange: recompileOnChange
    }
  });

  return build;
});
