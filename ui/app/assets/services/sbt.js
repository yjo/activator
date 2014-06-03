/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/streams', 'commons/events', 'commons/utils'], function(streams, events, utils) {

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

  return {

  };
})
