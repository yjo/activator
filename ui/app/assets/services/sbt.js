/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/streams', 'commons/events', 'commons/utils'], function(streams, events, utils) {

  function sbtRequest(what, o) {
    o.socketId = serverAppModel.socketId;

    var areq = {
      url: '/api/sbt/' + what,
      type: 'POST',
      dataType: 'json', // return type
      contentType: 'application/json; charset=utf-8',
      data: JSON.stringify(o)
    };

    return $.ajax(areq);
  }

  function possibleAutocompletions(partialCommand) {
    // TODO return something better (with the return value already parsed)
    return sbtRequest('possibleAutocompletions', {
      partialCommand: partialCommand
    });
  }

  function requestExecution(command) {
    // TODO return something better (with the return value already parsed)
    return sbtRequest('requestExecution', {
      command: command
    });
  }

  var executionsById = {};
  var executions = ko.observable([]);
  var tasksById = {};
  var tasks = ko.observable([]);

  var legacyLogHandlers = [];
  function legacySubscribeLog(handler) {
    legacyLogHandlers.push(handler);
  }

  function removeExecution(id, succeeded) {
    var execution = executionsById[id];
    if (execution) {
      executions.remove(function(item) {
        return item.executionId == execution.executionId;
      });
      // we want succeeded flag up-to-date when finished notifies
      execution.succeeded(event.success);
      execution.finished(true);
      delete executionsById[execution.executionId];
    }
  }

  var subTypeHandlers = {
      LogEvent: function(event) {
        // forward legacy log event TODO this is just a demo hack
        $.each(legacyLogHandlers, function(index, subscriber) {
            subscriber({
              'type' : 'LogEvent',
              'entry' : event.entry
            });
        });
      },
      TaskStarted: function(event) {
        debug && console.log("TaskStarted ", event);
        var task = {
            execution: executionsById[event.executionId],
            taskId: event.taskId,
            key: event.key ? event.key.key.name : null,
            finished: ko.observable(false),
            succeeded: ko.observable(false)
        };
        console.log("Starting task ", task);
        // we want to be in the by-id hash before we notify
        // on the tasks array
        tasksById[task.taskId] = task;
        tasks.push(task);
      },
      TaskFinished: function(event) {
        debug && console.log("TaskFinished ", event);
        var task = tasksById[event.taskId];
        if (task) {
          tasks.remove(function(item) {
            return item.taskId == task.taskId;
          });
          // we want succeeded flag up-to-date when finished notifies
          task.succeeded(event.success);
          task.finished(true);
          delete tasksById[task.taskId];
        }
      },
      ExecutionWaiting: function(event) {
        debug && console.log("ExecutionWaiting ", event);
        var execution = {
            executionId: event.id,
            command: event.command,
            started: ko.observable(false),
            finished: ko.observable(false),
            succeeded: ko.observable(false)
        };
        console.log("Waiting execution ", execution);
        // we want to be in the by-id hash before we notify
        // on the executions array
        executionsById[execution.executionId] = execution;
        executions.push(execution);
      },
      ExecutionStarting: function(event) {
        debug && console.log("ExecutionStarting ", event);
        var execution = executionsById[event.executionId];
        if (execution) {
          execution.started(true);
        }
      },
      ExecutionFailure: function(event) {
        debug && console.log("ExecutionFailure ", event);
        removeExecution(event.id, false /* succeeded */);
      },
      ExecutionSuccess: function(event) {
        debug && console.log("ExecutionSuccess ", event);
        removeExecution(event.id, true /* succeeded */);
      },
      CompilationFailure: function(event) {
        debug && console.log("CompilationFailure ", event);
      },
      TestEvent: function(event) {
        debug && console.log("TestEvent ", event);
      }
  }

  function eventHandler(obj) {
    debug && console.log("sbt event " + obj.subType, obj.event);
    if (obj.subType in subTypeHandlers) {
      subTypeHandlers[obj.subType](obj.event);
    } else {
      console.log("not handling sbt event of type " + obj.subType, obj.event);
    }
  }

  function isSbtEvent(obj) {
    return 'type' in obj && obj.type == 'sbt' && 'event' in obj && 'subType' in obj;
  }

  var eventSubscription = streams.subscribe({
    filter: isSbtEvent,
    handler: eventHandler
  });

  return {
    possibleAutocompletions: possibleAutocompletions,
    requestExecution: requestExecution,
    legacySubscribeLog: legacySubscribeLog,
    executions: executions,
    tasks: tasks
  };
})
