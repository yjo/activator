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
        self.developerKeyEnabled = ko.observable(false);
        self.licenseKey = ko.observable(appdynamics.licenseKey());
        self.downloadClass = ko.computed(function() {
          var enabled = (self.available() == false);
          self.downloadEnabled(enabled);
          return enabled ? "enabled" : "disabled";
        }, self);
        self.developerKeyClass = ko.computed(function() {
          var enabled = (self.available() == true);
          self.developerKeyEnabled(enabled);
          return enabled ? "enabled" : "disabled";
        }, self);
        self.provisionDownloadSubscription = ko.observable(null);
        self.downloading = ko.observable("");
        self.downloading.subscribe(function(value) {
          console.log("downloading: "+value);
        });
        self.provisionObserver = function(value) {
          var message = "";
          if (value.type == "provisioningError") {
            message = "Error provisioning New Relic: "+value.message
            self.downloading(message);
            self.error(message);
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
            appdynamics.provision()
          }
        };
        self.saveLicenseKey = function () {
          if (self.developerKeyEnabled() && !self.licenseKeyInvalid()) {
            appdynamics.licenseKey(self.licenseKey());
          }
        };
        self.resetKey = function () {
          self.licenseKey("");
          appdynamics.licenseKey("");
        };
        self.licenseKeyInvalid = ko.computed(function() {
          var key = self.licenseKey();
          return !appdynamics.validKey.test(key);
        }, self);
      }
    });

    return AppDynamics;
  });
