/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/utils', 'commons/widget', 'services/appdynamics', 'text!./appdynamics.html', 'css!./appdynamics.css'],
  function(utils, Widget, appdynamics, template, css){

    var AppDynamics = utils.Class(Widget,{
      id: 'appdynamics-widget',
      template: template,
      init: function(args) {
        var self = this;
        self.available = appdynamics.available;
        self.needProvision = ko.computed(function() {
          return !self.available();
        }, self);
        self.downloadEnabled = ko.observable(false);
        self.downloadClass = ko.computed(function() {
          var enabled = (self.available() == false);
          self.downloadEnabled(enabled);
          return enabled ? "enabled" : "disabled";
        }, self);
        self.provisionDownloadSubscription = ko.observable(null);
        self.downloading = ko.observable("");
        self.username = ko.observable("");
        self.usernameInvalid = ko.computed(function() {
          var key = self.username();
          return !appdynamics.validUsername.test(key);
        }, self);
        self.password = ko.observable("");
        self.passwordInvalid = ko.computed(function() {
          var key = self.password();
          return !appdynamics.validPassword.test(key);
        }, self);
        self.downloading.subscribe(function(value) {
          console.log("downloading: "+value);
        });
        self.provisionObserver = function(value) {
          var message = "";
          if (value.type == "provisioningError") {
            message = "Error provisioning AppDynamics: "+value.message;
            self.downloading(message);
            self.error(message);
          } else if (value.type == "authenticating") {
            self.downloading("Authenticating");
          } else if (value.type == "downloading") {
            self.downloading("Downloading: "+value.url);
          } else if (value.type == "progress") {
            message = "";
            if (value.percent) {
              message = value.percent.toFixed(0)+"%";
            } else {
              message = value.bytes+" bytes";
            }
            self.downloading("Progress: "+message);
          } else if (value.type == "downloadComplete") {
            self.downloading("Download complete");
          } else if (value.type == "validating") {
            self.downloading("Validating");
          } else if (value.type == "extracting") {
            self.downloading("Extracting");
          } else if (value.type == "complete") {
            self.downloading("Complete");
          } else {
            self.downloading("UNKNOWN STATE!!!");
          }

          if (value.type == "complete" || value.type == "provisioningError") {
            appdynamics.cancelObserveProvision(self.provisionDownloadSubscription());
            self.provisionDownloadSubscription(null);
          }
        };
        self.error = ko.observable();
        self.provisionAppDynamics = function () {
          if (self.downloadEnabled()) {
            self.provisionDownloadSubscription(appdynamics.observeProvision(self.provisionObserver));
            appdynamics.provision(self.username(),self.password());
          }
        };
        self.nodeName = ko.observable(appdynamics.nodeName());
        self.nodeName.subscribe(function (newValue) {
            self.saveNodeName(newValue);
          });
        self.tierName = ko.observable(appdynamics.tierName());
        self.tierName.subscribe(function (newValue) {
          self.saveTierName(newValue);
        });
        self.saveNodeName = function (newValue) {
          if (appdynamics.validNodeName.test(newValue)) {
            console.log("saving nodeName: "+newValue);
            appdynamics.nodeName(newValue);
          }
        };
        self.saveTierName = function (newValue) {
          if (appdynamics.validTierName.test(newValue)) {
            console.log("saving tierName: "+newValue);
            appdynamics.tierName(newValue);
          }
        };
        self.nodeNameInvalid = ko.computed(function() {
          var key = self.nodeName();
          return !appdynamics.validNodeName.test(key);
        }, self);
        self.tierNameInvalid = ko.computed(function() {
          var key = self.tierName();
          return !appdynamics.validTierName.test(key);
        }, self);
      }
    });

    return AppDynamics;
  });
