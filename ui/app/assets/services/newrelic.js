/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/utils', 'commons/streams', 'commons/settings', 'services/build'], function(utils, streams, settings, build) {

  var licenseKey = settings.observable("newrelic.licenseKey", "");

  function nrMessage(type) {
    return { request: 'NewRelicRequest', type: type };
  }

  function nrMessageWith(type,attributes) {
    return jQuery.extend(nrMessage(type), attributes);
  }

  var validKey = /[0-9A-Za-z]{40}/;

  var newRelic = utils.Singleton({
    init: function() {
      var self = this;
      self.validKey = validKey;
      self.isProjectEnabled = ko.observable("unknown");
      self.checkIsProjectEnabled = function() {
        streams.send(nrMessage("isProjectEnabled"));
      };
      self.hasPlay = ko.computed(function() {
        self.isProjectEnabled("unknown");
        return build.app.hasPlay();
      }, self);
      self.enableProject = function(key,name) {
        var message = nrMessageWith("enable",{key: key, name: name});
        console.log("message: "+JSON.stringify(message,null,2));
        streams.send(message);
      };
      self.observeProvision = function(observable) {
        return streams.subscribe({
          filter: function(event) {
            return event.response == 'ProvisioningStatus';
          },
          handler: function (event) {
            observable(event);
          }
        });
      };
      self.cancelObserveProvision = function(o) {
        streams.unsubscribe(o);
      };
      self.available = ko.observable("checking");
      streams.subscribe({
        filter: function(event) {
          return event.response == 'NewRelicResponse';
        },
        handler: function (event) {
          if (event.type == "availableResponse") {
            console.log("setting available to: " + event.result);
            self.available(event.result);
          }
          if (event.type == "provisioned") {
            console.log("New Relic provisioned");
            streams.send(nrMessage("available"));
          }
          if (event.type == "isProjectEnabledResponse") {
            console.log("Setting isProjectEnabled to: " + event.result);
            self.isProjectEnabled(event.result);
          }
          if (event.type == "projectEnabled") {
            console.log("Project enabled for New Relic");
            self.checkIsProjectEnabled();
          }
        }
      });
      self.onStreamOpen = function (handler) {
        streams.subscribe(function (event) {
          if (event.type == 'SourcesMayHaveChanged') {
            handler(event);
          }
        });
      };
      self.onStreamOpen(function (event) {
        console.log("Making initial request to check NR availability");
        streams.send(nrMessage("available"));
        self.checkIsProjectEnabled();
      });
      self.licenseKey = licenseKey;
      self.provision = function() {
        streams.send(nrMessage("provision"))
      };
      self.licenseKeySaved = ko.computed(function() {
        var key = self.licenseKey();
        return self.validKey.test(key);
      }, self);
    }
  });

  return newRelic;
});
