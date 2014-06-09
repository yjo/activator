/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(function() {
  var WEB_SOCKET_CLOSED = 'WebSocketClosed';
  var id = window.wsUrl;
  // We can probably just connect immediately.
  var WS;
  if ('MozWebSocket' in window) {
    WS = window.MozWebSocket;
  } else if ('WebSocket' in window) {
    WS = window.WebSocket;
  } else {
    WS = null;
    var message = "This browser does not support WebSocket, but currently Activator requires it. " +
    "Newer IE, Firefox, Safari, or Chrome should work for example.";
    // be sure we at least log it
    debug && console.log(message);
    // if jquery is screwy on the ancient browser, we still want to show the alert,
    // so put it in a try block.
    try {
      $('body').html('<div class="error" style="font-size: 24px; margin: 40px;"></div>');
      $('div.error').text(message);
    } catch(e) {
      debug && console.log("jquery not working either", e);
    }

    alert(message);

    // and give up, since this module isn't going to work.
    throw new Error(message);
  }

  debug && console.log("WS opening: " + id);
  var socket = new WS(id);

  var subscribers = [];

  var pendingPing = null;

  /** Sends a message down the event stream socket to the server.
   *  @param msg {object}
   */
  function sendMessage(msg) {
    socket.send(JSON.stringify(msg));
  }

  // Base filtering function to use in absense of any other.
  function allPass() {
    return true;
  }

  function randomShort() {
    return Math.floor(Math.random() * 65536)
  }

  function randomId() {
    return "listener-" + (new Date().getTime()) + "-" + randomShort() + "-" + randomShort() + "-" + randomShort();
  }

  /** Generic subscription service.
   * @param o {function|object} Either an event handling function or an object
   *                              consisting of:
   *                              - id (optional): The id used to deregister later
   *                              - handler: The event handler
   *                              - filter (optional): A filter on messages you wish to receive.
   *
   * @return {object}  The subscription information, including
   *                   the chosen filter, id and event handler.
   *                   Note: You need to remember the ID to unregister.
   */
  function subscribe(o) {
    var subscriber = {};
    if(typeof(o) == 'function') {
      subscriber.handler = o
    } else {
      subscriber = o;
    }
    subscriber.filter = o.filter || allPass;
    subscriber.id = o.id || randomId();
    subscribers.push(subscriber)
    return subscriber;
  }

  /**
   * Unsubscribes a message handler.
   *
   * @param o {String|Object}  Either the id of the listener, or the subscription object
   *                           returned by `subscribe` method.
   */
  function unsubscribe(o) {
    // Assume an object or a string
    var id = o.id || o;
    subscribers = $.grep(subscribers, function(subscriber, idx) {
      return subscriber.id = id;
    });

  }
  // Internal method that just sends events to subscribers.
  function sendEvent(event) {
    $.each(subscribers, function(idx, subscriber) {
      if(subscriber.filter(event)) {
        try {
          subscriber.handler(event);
        } catch(e) {
          debug && console.log('Handler ', subscriber, ' failed on message ', event, ' with error ', e);
        }
      }
    });
  }
  // Internal method to handle receiving websocket events.
  function receiveEvent(event) {
    // suppress LogEvent due to noisiness
    if (event.data.indexOf('"LogEvent"') < 0)
      debug && console.log("WS Event: ", event.data, event);
    var obj = JSON.parse(event.data);
    if ('response' in obj && obj.response == 'Pong') {
      if (pendingPing !== null) {
        if (obj.cookie != pendingPing.cookie) {
          debug && console.log("Pong cookie does not match! ", pendingPing, obj);
          // somehow there must be a different ping in progress
        } else {
          // this keeps the timeout from deciding our socket is dead
          debug && console.log("Got Pong response, socket is alive!")
          pendingPing = null;
        }
      } else {
        debug && console.log("not expecting a Pong right now");
      }
    } else {
      sendEvent(obj);
    }
  }

  function checkPing(delay) {
    if (pendingPing === null) {
      pendingPing = { request: "Ping", cookie: randomShort().toString() };
      debug && console.log("pinging websocket for live-ness cookie=" + pendingPing.cookie);
      var cookie = pendingPing.cookie; // save cookie in timeout closure
      sendMessage(pendingPing);
      setTimeout(function() {
        if (pendingPing !== null &&
          pendingPing.cookie === cookie) {
          debug && console.log("socket ping timed out; closing WebSocket since it appears hosed");
          // this should invoke our onclose() handler
          socket.close();
        }
      }, 7000);
    }
  }

  function onOpen(event) {
    debug && console.log("WS opened: ", event)
    checkPing();
    // re-ping every few minutes to catch it if the socket dies
    setInterval(function() {
      debug && console.log("Periodic websocket re-ping WS.OPEN=" + WS.OPEN + " readyState=" + socket.readyState);
      checkPing();
    }, 1000 * 60 * 5);
  }

  function onClose(event) {
    debug && console.log("WS closed: " + event.code + ": " + event.reason, event)

    // TODO it would be nicer to do some kind of in-DOM lightbox dialog with
    // two buttons like "Reload" and "Go away" (maybe it's useful to not reload
    // if you need to cut-and-paste some logs for example).

    // We send out a message on failure for anyone using us to handle.

    // This is in a timeout so that when we navigate away from the page we
    // don't flash the alert box briefly.
    setTimeout(function() {
      sendEvent({
        type: WEB_SOCKET_CLOSED,
        id: id
      });
    }, 3000);
  }

  function onError(event) {
    debug && console.log("WS error: ", event)
    // TODO do same as for closed?
  }

  socket.onopen = onOpen;
  socket.onmessage = receiveEvent;
  socket.onclose = onClose;
  socket.onerror = onError;


  subscribe({
    handler: function(event) {
      alert("Connection lost; you will need to reload the page or restart Activator.");
    },
    filter: function(event) {
      return event.type == WEB_SOCKET_CLOSED;
    }
  });

  // TODO - Create global event stream or some such so we can add listeners and fire events.
  return {
    // TODO - we need more public API then just "send message".
    send: sendMessage,
    subscribe: subscribe,
    unsubscribe: unsubscribe,
    WEB_SOCKET_CLOSED: WEB_SOCKET_CLOSED
  };
});
