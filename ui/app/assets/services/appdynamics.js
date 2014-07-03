/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/utils', 'commons/streams', 'commons/settings', 'services/build'], function(utils, streams, settings, build) {

  var nodeName = settings.observable("appDynamics.nodeName", "activator-"+new Date().getTime());
  var tierName = settings.observable("appDynamics.tierName", "development");
  var hostName = settings.observable("appDynamics.hostName", "");
  var port = settings.observable("appDynamics.port", 443);
  var sslEnabled = settings.observable("appDynamics.sslEnabled", true);
  var accountName = settings.observable("appDynamics.accountName", "");
  var accessKey = settings.observable("appDynamics.accessKey", "");

  function adMessage(type) {
    return { request: 'AppDynamicsRequest', type: type };
  }

  function adMessageWith(type,attributes) {
    return jQuery.extend(adMessage(type), attributes);
  }

  var validNodeName = /^[0-9a-z@\._-]{1,40}$/i;
  var validTierName = /^[0-9a-z@\._-]{1,40}$/i;
  var validUsername = /^.{1,40}$/i;
  var validPassword = /^[0-9a-z@\.,-\/#!$%\^&\*;:{}=\-_`~()]{1,40}$/i;
  var validPort = {
    test: function(v) {
      var n = Number(v);
      return (n > 0) && (n < 65536);
    }
  };
  var validAccountName = validNodeName;
  var validAccessKey = /^[0-9a-z]{12}$/i;
  var validHostName = /^[0-9a-z][0-9a-z\.\-$*_]{1,128}/i;

  var appDynamics = utils.Singleton({
    init: function() {
      var self = this;
      self.validNodeName = validNodeName;
      self.validTierName = validTierName;
      self.validUsername = validUsername;
      self.validPassword = validPassword;
      self.validPort = validPort;
      self.validAccountName = validAccountName;
      self.validAccessKey = validAccessKey;
      self.validHostName = validHostName;

      self.hostName = hostName;
      self.port = port;
      self.sslEnabled = sslEnabled;
      self.accountName = accountName;
      self.accessKey = accessKey;
      self.nodeName = nodeName;
      self.tierName = tierName;
      self.configured = ko.computed(function () {
        return (self.validNodeName.test(self.nodeName()) &&
        self.validTierName.test(self.tierName()) &&
        self.validPort.test(self.port()) &&
        self.validAccountName.test(self.accountName()) &&
        self.validAccessKey.test(self.accessKey()) &&
        self.validHostName.test(self.hostName()));
      }, self);
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
          return event.response == 'AppDynamicsResponse';
        },
        handler: function (event) {
          if (event.type == "availableResponse") {
            debug && console.log("setting available to: " + event.result);
            self.available(event.result);
          } else if (event.type == "provisioned") {
            debug && console.log("AppDynamics provisioned");
            streams.send(adMessage("available"));
          } else if (event.type == "deprovisioned") {
            debug && console.log("AppDynamics de-provisioned");
            streams.send(adMessage("available"));
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
        debug && console.log("Making initial request to check AD availability");
        streams.send(adMessage("available"));
      });
      self.provision = function(username,password) {
        streams.send(adMessageWith("provision",{username: username, password: password}))
      };
      self.deprovision = function() {
        streams.send(adMessage("deprovision"));
      };
      self.nodeNameSaved = ko.computed(function() {
        var name = self.nodeName();
        return self.validNodeName.test(name);
      }, self);
      self.tierNameSaved = ko.computed(function() {
        var name = self.tierName();
        return self.validTierName.test(name);
      }, self);
    }
  });

  return appDynamics;
});
