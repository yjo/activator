/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/settings',
  'services/build',
  "text!./navigation.html",
  "css!./navigation"
], function(
  settings,
  build,
  template
){

  $navigation = $(template)[0];

  var navigationOpened = settings.observable("app.navigationOpened", true);
  var sneak = ko.observable(false);
  var sneakTimer = 0;

  var toggle = function() {
    navigationOpened(!navigationOpened());
    sneak(navigationOpened());
  };
  var sneakOn = function() {
    if (!navigationOpened()) {
      sneak(true);
    }
  };
  var sneakShow = function() {
    clearTimeout(sneakTimer);
  };
  var sneakHide = function() {
    sneakTimer = setTimeout(function(){
      sneak(false);
    }, 500);
  };

  // Export to UI
  var NavigationState = {
    sneak: sneak,
    navigationOpened: navigationOpened,
    toggle: toggle,
    sneakOn: sneakOn,
    sneakShow: sneakShow,
    sneakHide: sneakHide,
    build: build
  }

  return {
    render: function(LayoutState) {
      ko.applyBindings($.extend(NavigationState,LayoutState), $navigation);
      return $navigation
    },
    state: NavigationState
  }

});
