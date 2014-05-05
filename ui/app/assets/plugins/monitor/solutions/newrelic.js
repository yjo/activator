/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['main/pluginapi', 'services/newrelic', 'text!./newrelic.html', 'css!./newrelic.css'],
  function(api, newrelic, template, css){

    var NewRelic = api.Class(api.Widget,{
      id: 'newrelic-widget',
      template: template,
      init: function(args) {
        var self = this;
        self.licenseKeySaved = newrelic.licenseKeySaved;
        self.available = newrelic.available;
        self.needProvision = ko.computed(function() {
          return !self.available() || !self.licenseKeySaved();
        }, self);
        self.downloadEnabled = ko.observable(false);
        self.developerKeyEnabled = ko.observable(false);
        self.licenseKey = ko.observable(newrelic.licenseKey());
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
            newrelic.cancelObserveProvision(self.provisionDownloadSubscription());
            self.provisionDownloadSubscription(null);
          }
        };
        self.error = ko.observable();
        self.provisionNewRelic = function () {
          if (self.downloadEnabled()) {
            self.provisionDownloadSubscription(newrelic.observeProvision(self.provisionObserver));
            newrelic.provision()
          }
        };
        self.saveLicenseKey = function () {
          if (self.developerKeyEnabled() && !self.licenseKeyInvalid()) {
            newrelic.licenseKey(self.licenseKey());
          }
        };
        self.resetKey = function () {
          self.licenseKey("");
          newrelic.licenseKey("");
        };
        self.licenseKeyInvalid = ko.computed(function() {
          var key = self.licenseKey();
          return !newrelic.validKey.test(key);
        }, self);
      }
    });

    return NewRelic;
  });
