define(['text!./deviation.html', 'commons/utils', './../widget', './../format', 'commons/templates', 'text!./tracetree.html'],
  function(template, utils, ConsoleWidget, formatter, templates, traceTreeTemplate) {

    var systemEvents = ["EventStreamError","EventStreamWarning","EventStreamDeadLetter","EventStreamUnhandledMessage",
                        "TopLevelActorRequested","TopLevelActorCreated","SysMsgDispatched","SysMsgReceived","SysMsgCompleted"];

    function isSystemEvent(type) {
        var index = $.inArray(type,systemEvents);
        return index >= 0;
    }

    function recreateMessage(message,prefix) { return prefix+"("+message+")"; }

    function extractMessage(message, sysMsgType) {
        var msgPrefix = "[Unknown]";
        if (sysMsgType != undefined) msgPrefix = sysMsgType;
        var result = "[unknown message]";
        if (message != undefined) {
            if (typeof message == "string") result = message;
            else if (message.cause != undefined) result = recreateMessage(message.cause,msgPrefix);
            else if (message.child != undefined && message.cause != undefined) result = recreateMessage(extractActorInfo(message.child)+", "+message.cause,msgPrefix);
            else if (message.child != undefined) result = recreateMessage(extractActorInfo(message.child),msgPrefix);
            else if (message.subject != undefined) result = recreateMessage(extractActorInfo(message.subject),msgPrefix);
            else if (message.watchee != undefined && message.watcher != undefined) result = recreateMessage(extractActorInfo(message.watchee)+", "+extractActorInfo(message.watcher),msgPrefix);
            else if (message.watched != undefined && message.existenceConfirmed != undefined && message.addressTerminated != undefined) result = recreateMessage(extractActorInfo(message.watched)+", "+message.existenceConfirmed+", "+message.addressTerminated,msgPrefix);
            else if (typeof message == "object") result = msgPrefix;
        } else result = "";
        return result;
    }

    var actorEvents = ["SysMsgDispatched","SysMsgReceived","SysMsgCompleted","ActorRequested","ActorCreated","ActorTold",
                       "ActorAutoReceived","ActorAutoCompleted","ActorReceived","ActorCompleted","ActorAsked","ActorFailed","TempActorCreated",
                       "TempActorStopped","ActorSelectionTold","RemoteMessageSent","RemoteMessageReceived","RemoteMessageCompleted",
                       "EventStreamDeadLetter","EventStreamUnhandledMessage"];

    function isActorEvent(event) {
        var index = $.inArray(event.type, actorEvents);
        return index >= 0;
    }

    function formatTime(time) { return formatter.formatTime(new Date(time)); }

    function extractTrace(trace) {
        var result = "N/A";
        if (trace != undefined) result = trace.substring(trace.lastIndexOf("/") + 1);
        return result;
    }

    var failureEvents = ["ActorFailed","EventStreamError","EventStreamWarning","EventStreamDeadLetter",
                         "EventStreamUnhandledMessage", "DeadlockedThreads"];

    function isFailureEvent(type) {
        return ($.inArray(type,failureEvents) >= 0);
    }

    function extractActorInfo(info) {
        var result = "N/A";
        if (info != undefined) result = info.actorPath;
        return result;
    }

    function extractActorPath(annotation) {
        var result = "N/A";
        if (annotation != undefined) result = extractActorInfo(annotation.actorInfo);
        return result;
    }

      return utils.Class(ConsoleWidget, {
        id: 'console-deviation-widget',
        template: template,
        classInit: function(proto) {
            // Register trace tree template that is used in the deviation view
            templates.registerTemplate("trace-tree-template", traceTreeTemplate);
        },
        init: function(args) {
            var self = this;
            this.eventId = "unknown";
            this.deviationTypeName = "unknown";
            this.parameters = function(params) {
                this.deviationTypeName = params[0];
                this.deviationType(params[0]);
                this.eventId = params[1];
            };
            this.dataFound = ko.observable(true);
            this.deviationType = ko.observable();
            this.deviationTime = ko.observable();
            this.deviationActorPath = ko.observable()
            this.deviationReason = ko.observable();
            this.errorMessage = ko.observable();
            this.deadlocks = ko.observable();
            this.messageFrom = ko.observable();
            this.messageTo = ko.observable();
            this.showSystemMessages = ko.observable(false);
            this.showNanoSeconds = ko.observable(false);
            this.showActorSystems = ko.observable(false);
            this.showTraceInformation = ko.observable(false);
            this.traceTree = ko.observable();

            // Helper functions for the trace tree template
            this.showOnlyMessage = function() {
                return this.deviationTypeName == "Warning";
            }
            this.showDeadlocks = function() {
                return this.deviationTypeName == "Deadlock";
            }
            this.showActorInfo = function() {
                return this.deviationTypeName == "Error";
            }
            this.showFromTo = function() {
                return this.deviationTypeName == "Deadletter" || this.deviationTypeName == "Unhandled Message";
            }
            this.isSystemEvent = isSystemEvent
            this.recreateMessage = recreateMessage
            this.extractMessage = extractMessage
            this.isActorEvent = isActorEvent
            this.showEvent = function(type) { return !(isSystemEvent(type) && !self.showSystemMessages()); }
            this.formatTime = formatTime
            this.extractTrace = extractTrace
            this.isFailureEvent = isFailureEvent
            this.extractActorInfo = extractActorInfo
            this.extractActorPath = extractActorPath
            // End: Helper functions
        },
        dataName: 'deviation',
        dataTypes: ['deviation'],
        dataScope: {},
        dataRequest: function() {
            return { 'eventId': this.eventId };
        },
        onData: function(data) {
            this.extractReason = function(json) {
                var event = json.event;
                if (event && event.annotation && event.annotation.reason != undefined) {
                    this.deviationTime(formatter.formatTime(new Date(event.timestamp)));
                    if (event.annotation.actorInfo != undefined) {
                        this.deviationActorPath(event.annotation.actorInfo.actorPath);
                    }
                    this.deviationReason(event.annotation.reason);
                } else {
                   if (json.children != undefined && json.children.length > 0) {
                       for(var i = 0; i < json.children.length; i++) {
                            this.extractReason(json.children[i]);
                       }
                   }
                }
            }
            this.extractFromToInfo = function(json) {
                var event = json.event;
                if (event && event.type && (event.type == "EventStreamDeadLetter" || event.type == "EventStreamUnhandledMessage")) {
                    this.deviationTime(formatter.formatTime(new Date(event.timestamp)));
                    this.errorMessage(event.annotation.message);
                    this.messageFrom(event.annotation.sender.actorPath);
                    this.messageTo(event.annotation.recipient.actorPath);
                } else {
                   if (json.children != undefined && json.children.length > 0) {
                       for(var i = 0; i < json.children.length; i++) {
                            this.extractFromToInfo(json.children[i]);
                       }
                   }
                }
            }
            this.extractWarning = function(json) {
                var event = json.event;
                if (event && event.type && (event.type == "EventStreamWarning" || event.type == "EventStreamError")) {
                    this.deviationReason(event.annotation.message);
                } else {
                   if (json.children != undefined && json.children.length > 0) {
                       for(var i = 0; i < json.children.length; i++) {
                            this.extractWarning(json.children[i]);
                       }
                   }
                }
            }
            this.extractDeadlock = function(json) {
                var event = json.event;
                if (event && event.type && event.type == "DeadlockedThreads") {
                    this.deviationReason(event.annotation.message);
                    this.deadlocks(event.annotation.join("\n"));
                } else {
                   if (json.children != undefined && json.children.length > 0) {
                       for(var i = 0; i < json.children.length; i++) {
                            this.extractWarning(json.children[i]);
                       }
                   }
                }
            }

            this.deviationReason(this.deviationTypeName);
            this.deviationActorPath("[unknown]");
            this.deviationTime("[unknown]");
            this.errorMessage("[unknown]");
            this.messageFrom("[unknown]");
            this.messageTo("[unknown]");
            this.deadlocks("[unknown]");

            if (data != null) {
                this.dataFound(true);
                if (this.deviationTypeName == "Error") {
                    this.extractReason(data.traceTree);
                } else if (this.deviationTypeName == "Deadletter" || this.deviationTypeName == "Unhandled Message") {
                   this.extractFromToInfo(data.traceTree);
                } else if (this.deviationTypeName == "Warning") {
                   this.extractWarning(data.traceTree);
                } else if (this.deviationTypeName == "Deadlock") {
                   this.extractDeadlock(data.traceTree);
                }
                this.traceTree(data.traceTree);
            } else {
                this.dataFound(false);
            }
        }
    });
});
