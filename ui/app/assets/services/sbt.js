/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/streams', 'commons/events', 'commons/utils'], function(streams, events, utils) {

  console.log("loading sbt.js");

  function sbtRequest(what, o) {
    o.appId = serverAppModel.id

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

  function eventHandler(obj) {
    console.log("sbt event " + obj.subType, obj.event);
  }

  function isSbtEvent(obj) {
    return 'type' in obj && obj.type == 'sbt' && 'event' in obj && 'subType' in obj;
  }

  var eventSubscription = streams.subscribe({
    filter: isSbtEvent,
    handler: eventHandler
  });

  // TODO do something with the events (like track a model or stream of tasks and logs)

  // TODO this is just a temporary hack so we can see something happen
  // without wiring up the compile button...
  requestExecution('compile');

  return {
    possibleAutocompletions: possibleAutocompletions,
    requestExecution: requestExecution
  };
})
