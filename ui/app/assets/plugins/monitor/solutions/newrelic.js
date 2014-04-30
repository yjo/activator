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
        this.provisionNewRelic = function () {
          if (this.downloadEnabled()) {
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

        // TODO provide download feed back
        this.downloading = ko.observable(10);
        // TODO provide true errors
        this.error = ko.observable("Can not download the agent.");
      }
    });

    return NewRelic;
  });
