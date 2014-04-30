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
        this.licenseKeySaved = newrelic.licenseKeySaved;
        this.available = newrelic.available;
        this.needProvision = ko.computed(function() {
          return !this.available() || !this.licenseKeySaved();
        }, this);
        this.downloadEnabled = ko.observable(false);
        this.developerKeyEnabled = ko.observable(false);
        this.licenseKey = ko.observable(newrelic.licenseKey());
        this.downloadClass = ko.computed(function() {
          var enabled = (this.available() == false);
          this.downloadEnabled(enabled);
          return enabled ? "enabled" : "disabled";
        }, this);
        this.developerKeyClass = ko.computed(function() {
          var enabled = (this.available() == true);
          this.developerKeyEnabled(enabled);
          return enabled ? "enabled" : "disabled";
        }, this);
        this.provisionDownloadSubscription = ko.observable(null);
        this.provisionObserver = ko.observable();
        this.downloading = ko.observable("Hello");
        this.provisionObserver.subscribe(function(value) {
          console.log(value);
          if (value.type == "provisioningError") {
            var message = "Error provisioning New Relic: "+value.message
            this.downloading(message);
            this.error(message);
          } else if (value.type == "downloading") {
            this.downloading("downloading: "+value.url);
          } else if (value.type == "progress") {
            var message = "";
            if (value.percent) {
              message = value.percent.toFixed(2)+"%";
            } else {
              message = value.bytes+" bytes";
            }
            this.downloading(message);
          } else if (value.type == "downloadComplete") {
            this.downloading("download complete");
          } else if (value.type == "validating") {
            this.downloading("validating");
          } else if (value.type == "extracting") {
            this.downloading("extracting");
          } else if (value.type == "complete") {
            this.downloading("complete");
          } else {
            this.downloading("UNKNOWN STATE!!!");
          }

          if (value.type == "complete" || value.type == "provisioningError") {
            newrelic.cancelObserveProvision(this.provisionDownloadSubscription());
            this.provisionDownloadSubscription(null);
          }
        });
        this.error = ko.observable();
        this.provisionNewRelic = function () {
          if (this.downloadEnabled()) {
            this.provisionDownloadSubscription(newrelic.observeProvision(this.provisionObserver));
            newrelic.provision()
          }
        };
        this.saveLicenseKey = function () {
          if (this.developerKeyEnabled() && !this.licenseKeyInvalid()) {
            newrelic.licenseKey(this.licenseKey());
          }
        };
        this.resetKey = function () {
          this.licenseKey("");
          newrelic.licenseKey("");
        };
        this.licenseKeyInvalid = ko.computed(function() {
          var key = this.licenseKey();
          return !newrelic.validKey.test(key);
        }, this);
      }
    });

    return NewRelic;
  });
