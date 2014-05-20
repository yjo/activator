/**
 * Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
require.config({
  baseUrl:  '/public',
  // hack for now due to problem loading plugin loaders from a plugin loader
  map: {
    '*': {
      'css': '../../webjars/require-css/0.0.7/css',
      'text': '../../webjars/requirejs-text/2.0.10/text'
    }
  },
  paths: {
    jquery: 'vendors/jquery',
    ko: 'vendors/knockout-3.0.0'
  }
});

var vendors = [
  'webjars!jquery',
  'webjars!knockout',
  'webjars!ace',
  'commons/visibility',
  '../../webjars/requirejs-text/2.0.10/text',
  '../../webjars/require-css/0.0.7/css'
]

var commons = [
  'commons/templates',
  'commons/effects',
  'commons/utils',
  'commons/settings',
  'commons/streams',
  'commons/events'
]

var services = [
    'services/sbt',
    'services/build',
    'services/log',
    'services/tutorial',
    'services/connection',
    'widgets/notifications/notifications',
    'services/typesafe'
]

var core = [
  'main/view',
  'main/router',
  'main/keyboard'
]

require(vendors, function($, ko) {
  window.ko = ko; // it's used on every page...
  require(commons, function() {
    require(services, function() {
      require(core, function(view, router) {
        view.render();
        router.load(window.location.hash)
      })
    })
  })
})
