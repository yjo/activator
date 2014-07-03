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
            self.error("");
            self.provisionDownloadSubscription(appdynamics.observeProvision(self.provisionObserver));
            appdynamics.provision(self.username(),self.password());
          }
        };
        self.deprovisionAppDynamics = function () {
          if (!self.downloadEnabled()) {
            appdynamics.deprovision();
          }
        };
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
        self.saveHostName = function (newValue) {
          if (appdynamics.validHostName.test(newValue)) {
            console.log("saving hostName: "+newValue);
            appdynamics.hostName(newValue);
          }
        };
        self.savePort = function (newValue) {
          if (appdynamics.validPort.test(newValue)) {
            console.log("saving port: "+newValue);
            appdynamics.port(newValue);
          }
        };
        self.saveAccountName = function (newValue) {
          if (appdynamics.validAccountName.test(newValue)) {
            console.log("saving accountName: "+newValue);
            appdynamics.accountName(newValue);
          }
        };
        self.saveAccessKey = function (newValue) {
          if (appdynamics.validAccessKey.test(newValue)) {
            console.log("saving accessKey: "+newValue);
            appdynamics.accessKey(newValue);
          }
        };

        self.hostName = ko.observable((function () {
          var hn = appdynamics.hostName();
          if (typeof(hn) == 'undefined' || hn == null || hn == "") {
            return ".saas.appdynamics.com";
          } else {
            return hn;
          }
        })());
        self.hostName.subscribe(self.saveHostName);
        self.port = ko.observable(appdynamics.port());
        self.port.subscribe(self.savePort);
        self.sslEnabled = appdynamics.sslEnabled;
        self.accountName = ko.observable(appdynamics.accountName());
        self.accountName.subscribe(self.saveAccountName);
        self.accessKey = ko.observable(appdynamics.accessKey());
        self.accessKey.subscribe(self.saveAccessKey);
        self.nodeName = ko.observable(appdynamics.nodeName());
        self.nodeName.subscribe(self.saveNodeName);
        self.tierName = ko.observable(appdynamics.tierName());
        self.tierName.subscribe(self.saveTierName);

        self.nodeNameInvalid = ko.computed(function() {
          return !appdynamics.validNodeName.test(self.nodeName());
        }, self);
        self.tierNameInvalid = ko.computed(function() {
          return !appdynamics.validTierName.test(self.tierName());
        }, self);
        self.hostNameInvalid = ko.computed(function() {
          return !appdynamics.validHostName.test(self.hostName());
        }, self);
        self.portInvalid = ko.computed(function () {
          return (!appdynamics.validPort.test(self.port()));
        },self);
        self.accountNameInvalid = ko.computed(function () {
          return (!appdynamics.validAccountName.test(self.accountName()));
        },self);
        self.accessKeyInvalid = ko.computed(function () {
          return (!appdynamics.validAccessKey.test(self.accessKey()));
        },self);

        self.configured = ko.computed(function () {
          return (appdynamics.validNodeName.test(self.nodeName()) &&
          appdynamics.validTierName.test(self.tierName()) &&
          appdynamics.validPort.test(self.port()) &&
          appdynamics.validAccountName.test(self.accountName()) &&
          appdynamics.validAccessKey.test(self.accessKey()) &&
          appdynamics.validHostName.test(self.hostName()));
        }, self);
      }
    });

    return AppDynamics;
  });
