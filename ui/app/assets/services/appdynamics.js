/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/utils', 'commons/streams', 'commons/settings', 'services/build'], function(utils, streams, settings, build) {

  function adMessage(type) {
    return { request: 'AppDynamicsRequest', type: type };
  }

  function adMessageWith(type,attributes) {
    return jQuery.extend(adMessage(type), attributes);
  }

  var appDynamics = utils.Singleton({
    init: function() {
      var self = this;
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
            console.log("setting available to: " + event.result);
            self.available(event.result);
          }
          if (event.type == "provisioned") {
            console.log("AppDynamics provisioned");
            streams.send(adMessage("available"));
          }
        }
      });
      console.log("Making initial request to check AD availability");
      streams.send(adMessage("available"));
      self.provision = function() {
        streams.send(adMessage("provision"))
      };
    }
  });

  return appDynamics;
});
