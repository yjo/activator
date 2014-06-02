/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./welcome.html', 'css!./welcome.css'], function(template, css){


  var WelcomeState = (function(){
    var self = {};

    self.appVersion = window.serverAppVersion
    self.newsHtml = ko.observable('<div></div>');

    self.loadNews = function() {
      var areq = {
          url: "http://downloads.typesafe.com/typesafe-activator/" + window.serverAppVersion + "/news.js",
          type: 'GET',
          // this is hardcoded for now since our server is just static files
          // so can't respect a ?callback= query param.
          jsonpCallback: 'setNewsJson',
          dataType: 'jsonp' // return type
        };
        debug && console.log("sending ajax news request ", areq);
        return $.ajax(areq);
    };
    self.setNewsJson = function(json) {
      debug && console.log("setting news json to ", json);
      if ('html' in json) {
        this.newsHtml(json.html);
      } else {
        console.error("json does not have an html field");
      }
    };

    self.loadNews();
    return self;
  }());

  window.setNewsJson = WelcomeState.setNewsJson.bind(WelcomeState);

  return {
    render: function() {
      var $welcome = $(template)[0];
      ko.applyBindings(WelcomeState, $welcome);
      return $welcome;
    },
    route: function(){}
  }

});
